package io.testchimp.rum

/** Init configuration (mirrors Swift / @testchimp/rum-js `InitConfig`). */
data class TestChimpRumConfig(
    val projectId: String,
    val apiKey: String,
    val environment: String,
    val sessionId: String? = null,
    val release: String? = null,
    val branchName: String? = null,
    val sessionMetadata: Map<String, Any>? = null,
    /** Advanced knobs (mirrors Swift `Inner` / JS `config`). */
    val options: Options? = null,
) {
    data class Options(
        val captureEnabled: Boolean? = null,
        val enableDefaultSessionMetadata: Boolean? = null,
        val maxEventsPerSession: Int? = null,
        val maxRepeatsPerEvent: Int? = null,
        val eventSendIntervalMillis: Long? = null,
        val maxBufferSize: Int? = null,
        val inactivityTimeoutMillis: Long? = null,
        val testchimpEndpoint: String? = null,
        val automationContextTtlSeconds: Double? = null,
    )
}

/** Single event (mirrors `EmitInput`). */
data class TestChimpEmitInput(
    val title: String,
    val metadata: Map<String, Any>? = null,
)
