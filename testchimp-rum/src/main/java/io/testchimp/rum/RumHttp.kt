package io.testchimp.rum

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object RumHttp {
    const val RUM_PLATFORM_HEADER = "testchimp-rum-platform"
    /** {@link com.aware.protos.common.ExecutionPlatform} ordinal for Android. */
    const val ANDROID_PLATFORM_ORDINAL = 3

    fun postJson(
        baseUrl: String,
        path: String,
        body: JSONObject,
        projectId: String,
        apiKey: String,
        ciTestInfo: String?,
        rumPlatform: Int = ANDROID_PLATFORM_ORDINAL,
    ) {
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Project-Id", projectId)
            setRequestProperty("TestChimp-Api-Key", apiKey)
            if (!ciTestInfo.isNullOrEmpty()) {
                setRequestProperty("ci-test-info", ciTestInfo)
            }
            setRequestProperty(RUM_PLATFORM_HEADER, rumPlatform.toString())
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            // best-effort; mirrors rum-js swallow
        } finally {
            conn.disconnect()
        }
    }
}
