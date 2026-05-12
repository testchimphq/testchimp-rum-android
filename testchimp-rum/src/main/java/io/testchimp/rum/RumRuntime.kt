package io.testchimp.rum

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class RumRuntime(
    context: Context,
    private val config: TestChimpRumConfig,
) {
    private val appContext = context.applicationContext
    private val sessionStore = SessionStore(context)
    private val automation = AutomationContext()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "testchimp-rum").apply { isDaemon = true }
    }
    private val flushScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "testchimp-rum-flush").apply { isDaemon = true }
    }

    @Volatile
    var storedSessionId: String = ""
        private set

    private val opts = config.options
    private var captureEnabled = opts?.captureEnabled ?: true
    private var maxEventsPerSession = opts?.maxEventsPerSession ?: 100
    private var maxRepeatsPerEvent = opts?.maxRepeatsPerEvent ?: 3
    private var maxBufferSize = opts?.maxBufferSize ?: 100
    private var eventSendIntervalMs = opts?.eventSendIntervalMillis ?: 10_000L
    private var inactivityTimeoutMs = opts?.inactivityTimeoutMillis ?: (30 * 60 * 1000L)
    private var baseUrl = (opts?.testchimpEndpoint?.trimEnd('/') ?: "https://ingress.testchimp.io")

    private var flushTask: ScheduledFuture<*>? = null

    private data class BufferedEvent(
        val title: String,
        val timestampMillis: Long,
        val metadata: JSONObject?,
        val eventIndex: Int,
        val ciTestInfoSnapshot: String?,
    )

    private val buffer = mutableListOf<BufferedEvent>()

    init {
        opts?.automationContextTtlSeconds?.let { if (it > 0) automation.ttlSeconds = it }
    }

    fun start() {
        val normalizedMeta = RumValidation.normalizeMetadataFromMap(config.sessionMetadata)
        val (sid, isNew) = sessionStore.loadOrCreateSessionId(
            config.sessionId,
            normalizedMeta,
            inactivityTimeoutMs,
        )
        storedSessionId = sid

        if (isNew && captureEnabled) {
            executor.submit {
                try {
                    sendSessionStart()
                } catch (_: Throwable) {
                }
            }
        }

        flushTask = flushScheduler.scheduleAtFixedRate(
            { executor.submit { flushLocked() } },
            eventSendIntervalMs,
            eventSendIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun tearDown() {
        flushTask?.cancel(false)
        flushTask = null
        flushScheduler.shutdownNow()
        try {
            executor.submit {
                flushLocked()
                sessionStore.clearAll()
                automation.clear()
                buffer.clear()
            }.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // ignore
        }
        executor.shutdownNow()
    }

    fun handleAutomationUri(uri: android.net.Uri?): Boolean {
        if (isTrueCoverageClear(uri)) {
            try {
                executor.submit {
                    flushLocked()
                    automation.clear()
                }.get(2, TimeUnit.SECONDS)
            } catch (_: Exception) {
                automation.clear()
            }
            return true
        }
        return try {
            executor.submit(Callable { AutomationUri.handle(uri, automation) })
                .get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            AutomationUri.handle(uri, automation)
        }
    }

    fun clearAutomationContext() {
        try {
            executor.submit {
                flushLocked()
                automation.clear()
            }.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            automation.clear()
        }
    }

    /**
     * Fire-and-forget: reads current TrueCoverage CI on the caller thread (rum-js / `__TC_CI_TEST_INFO` parity),
     * then enqueues buffer/network work. Never throws to the app.
     */
    fun emit(input: TestChimpEmitInput) {
        try {
            if (!captureEnabled) return
            if (RumValidation.buildEmitPayload(input.title, input.metadata) == null) return
            val ciSnap = try {
                automation.snapshotForEmit()
            } catch (_: Throwable) {
                null
            }
            try {
                executor.submit {
                    try {
                        emitOnExecutor(input, ciSnap)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    private fun emitOnExecutor(input: TestChimpEmitInput, ciSnap: String?) {
        // BufferedEvent.ciTestInfoSnapshot must use ciSnap only — TrueCoverage CI was captured on the
        // caller thread in emit(); do not call automation.snapshotForEmit() here (would re-open ordering
        // bugs vs clear/set on this same executor).
        sessionStore.touchActivity()
        val title = input.title
        if (sessionStore.eventCount() >= maxEventsPerSession) return
        val counts = sessionStore.eventTypeCounts()
        if ((counts[title] ?: 0) >= maxRepeatsPerEvent) return

        val ts = System.currentTimeMillis()
        val meta = RumValidation.normalizeMetadataFromMap(input.metadata)

        val next = sessionStore.eventCount() + 1
        sessionStore.setEventCount(next)
        counts[title] = (counts[title] ?: 0) + 1
        sessionStore.setEventTypeCounts(counts)

        buffer.add(
            BufferedEvent(
                title = title,
                timestampMillis = ts,
                metadata = meta,
                eventIndex = next,
                ciTestInfoSnapshot = ciSnap,
            ),
        )
        if (buffer.size >= maxBufferSize) {
            flushLocked()
        }
    }

    fun flush(wait: Boolean) {
        if (wait) {
            val done = java.util.concurrent.CountDownLatch(1)
            executor.submit {
                try {
                    flushLocked()
                } finally {
                    done.countDown()
                }
            }
            try {
                done.await(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        } else {
            executor.submit { flushLocked() }
        }
    }

    private fun flushLocked() {
        if (buffer.isEmpty()) return
        val batch = ArrayList(buffer)
        buffer.clear()
        try {
            postEvents(batch)
        } catch (_: Throwable) {
        }
    }

    private fun sendSessionStart() {
        val meta = JSONObject()
        if (opts?.enableDefaultSessionMetadata ?: true) {
            copyJsonKeys(DefaultSessionMetadata.forSessionStart(appContext), meta)
        }
        sessionStore.sessionMetadata()?.let { copyJsonKeys(it, meta) }

        val body = JSONObject()
        body.put("session_id", storedSessionId)
        body.put("started_at", System.currentTimeMillis())
        body.put("metadata", meta)
        body.put("environment", config.environment)
        config.release?.let { body.put("release", it) }
        config.branchName?.let { body.put("branch_name", it) }

        val ci = automation.snapshotForEmit()
        RumHttp.postJson(baseUrl, "/rum/session/start", body, config.projectId, config.apiKey, ci)
    }

    private fun postEvents(events: List<BufferedEvent>) {
        for (partition in partitionByCiSnapshot(events)) {
            val arr = JSONArray()
            for (e in partition) {
                val o = JSONObject()
                o.put("title", e.title)
                o.put("event_index", e.eventIndex)
                o.put("timestamp_millis", e.timestampMillis)
                o.put("metadata", e.metadata ?: JSONObject())
                arr.put(o)
            }
            val body = JSONObject()
            body.put("session_id", storedSessionId)
            body.put("events", arr)
            val ciHeader = partition.firstOrNull()?.ciTestInfoSnapshot
            RumHttp.postJson(baseUrl, "/rum/events", body, config.projectId, config.apiKey, ciHeader)
        }
    }

    private fun partitionByCiSnapshot(events: List<BufferedEvent>): List<List<BufferedEvent>> {
        if (events.isEmpty()) return emptyList()
        val out = mutableListOf<MutableList<BufferedEvent>>()
        var current = mutableListOf(events.first())
        var currentCi = events.first().ciTestInfoSnapshot
        for (i in 1 until events.size) {
            val e = events[i]
            if (e.ciTestInfoSnapshot == currentCi) {
                current.add(e)
                continue
            }
            out.add(current)
            current = mutableListOf(e)
            currentCi = e.ciTestInfoSnapshot
        }
        out.add(current)
        return out
    }

    private fun isTrueCoverageClear(uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme?.lowercase() != "testchimp-rum") return false
        if (uri.host?.lowercase() != "truecoverage") return false
        return uri.path?.lowercase() == "/v1/clear"
    }

    /** `JSONObject.keys()` is a [java.util.Iterator]; avoid Kotlin `for (k in keys)` misuse. */
    private fun copyJsonKeys(from: JSONObject, to: JSONObject) {
        val it = from.keys()
        while (it.hasNext()) {
            val k = it.next()
            to.put(k, from.get(k))
        }
    }
}
