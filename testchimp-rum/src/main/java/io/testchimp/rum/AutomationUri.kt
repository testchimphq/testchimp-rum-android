package io.testchimp.rum

import android.net.Uri
import android.util.Base64

/** `testchimp-rum://truecoverage/v1/set?p=<base64url>` and `.../v1/clear` — matches iOS + @testchimp/playwright defaults. */
internal object AutomationUri {
    fun handle(uri: Uri?, context: AutomationContext): Boolean {
        if (uri == null) return false
        if (uri.scheme?.lowercase() != "testchimp-rum") return false
        if (uri.host?.lowercase() != "truecoverage") return false

        val path = uri.path?.lowercase() ?: return false
        if (path == "/v1/clear") {
            context.clear()
            return true
        }
        if (path == "/v1/set") {
            val p = uri.getQueryParameter("p") ?: return false
            val bytes = decodeBase64Url(p) ?: return false
            val json = bytes.toString(Charsets.UTF_8)
            if (json.isEmpty()) return false
            context.setCiTestInfoJson(json)
            return true
        }
        return false
    }

    private fun decodeBase64Url(s: String): ByteArray? {
        return try {
            Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            var str = s.replace('-', '+').replace('_', '/')
            val pad = (4 - str.length % 4) % 4
            if (pad > 0) str += "=".repeat(pad)
            try {
                Base64.decode(str, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
