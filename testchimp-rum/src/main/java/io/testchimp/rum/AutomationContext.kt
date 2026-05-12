package io.testchimp.rum

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** In-process CI metadata for the `ci-test-info` header (TrueCoverage). */
internal class AutomationContext {
    private val lock = ReentrantLock()
    private var ciTestInfoJson: String? = null
    private var updatedAtSeconds: Double = 0.0

    /** Seconds after which automation context is dropped. */
    var ttlSeconds: Double = 900.0

    fun setCiTestInfoJson(json: String) {
        lock.withLock {
            ciTestInfoJson = json
            updatedAtSeconds = System.currentTimeMillis() / 1000.0
        }
    }

    fun clear() {
        lock.withLock {
            ciTestInfoJson = null
        }
    }

    /**
     * Snapshot of current CI JSON for the `ci-test-info` header. Thread-safe from any thread
     * (e.g. caller thread at [RumRuntime.emit] entry before work is queued).
     */
    fun snapshotForEmit(): String? {
        lock.withLock {
            val j = ciTestInfoJson ?: return null
            val now = System.currentTimeMillis() / 1000.0
            if (now - updatedAtSeconds > ttlSeconds) {
                ciTestInfoJson = null
                return null
            }
            return j
        }
    }

    /** Same eligibility as [snapshotForEmit] without allocating the JSON string (host debug logging). */
    fun hasActiveCiTestInfo(): Boolean {
        lock.withLock {
            val j = ciTestInfoJson ?: return false
            if (j.isEmpty()) return false
            val now = System.currentTimeMillis() / 1000.0
            if (now - updatedAtSeconds > ttlSeconds) {
                ciTestInfoJson = null
                return false
            }
            return true
        }
    }
}
