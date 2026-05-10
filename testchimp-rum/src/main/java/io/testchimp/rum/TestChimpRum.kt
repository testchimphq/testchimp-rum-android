package io.testchimp.rum

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * TestChimp RUM for Android — same ingest contract as [@testchimp/rum-js](https://www.npmjs.com/package/@testchimp/rum-js).
 *
 * Call [initialize] from `Application.onCreate` (or before first [emit]).
 */
object TestChimpRum {
    private val lock = Any()
    @Volatile
    private var runtime: RumRuntime? = null

    /**
     * @param context Any context; [android.content.Context.getApplicationContext] is used internally.
     */
    @JvmStatic
    fun initialize(context: Context, config: TestChimpRumConfig) {
        if (config.projectId.isBlank() || config.apiKey.isBlank()) {
            return
        }
        synchronized(lock) {
            runtime?.tearDown()
            val r = RumRuntime(context, config)
            runtime = r
            r.start()
        }
    }

    @JvmStatic
    fun emit(input: TestChimpEmitInput) {
        val r = runtime ?: return
        r.emit(input)
    }

    @JvmStatic
    fun flush() {
        runtime?.flush(wait = false)
    }

    @JvmStatic
    fun getSessionId(): String = runtime?.storedSessionId ?: ""

    /** Clears session and stops background work; call [initialize] again to resume. */
    @JvmStatic
    fun resetSession() {
        synchronized(lock) {
            val r = runtime
            runtime = null
            r?.tearDown()
        }
    }

    /** Handle deep link from Mobilewright / `@testchimp/playwright` (`device.openUrl`). */
    @JvmStatic
    fun handleAutomationUri(uri: Uri?): Boolean {
        val r = runtime ?: return false
        return r.handleAutomationUri(uri)
    }

    @JvmStatic
    fun handleAutomationIntent(intent: Intent?): Boolean {
        return handleAutomationUri(intent?.data)
    }

    @JvmStatic
    fun clearAutomationContext() {
        runtime?.clearAutomationContext()
    }
}
