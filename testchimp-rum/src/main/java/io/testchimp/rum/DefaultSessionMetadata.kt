package io.testchimp.rum

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

/** Client-derived session init metadata (mirrors @testchimp/rum-js defaults + mobile fields). */
internal object DefaultSessionMetadata {
    private const val MAX_STRING_LEN = 200

    private fun trunc(s: String): String =
        if (s.length <= MAX_STRING_LEN) s else s.substring(0, MAX_STRING_LEN)

    fun forSessionStart(context: Context): JSONObject {
        val m = JSONObject()
        m.put("_platform", "android")
        m.put("_os", "android")
        m.put("_device_type", deviceType(context))
        m.put("_language", trunc(Locale.getDefault().toLanguageTag()))
        m.put("_timezone", trunc(TimeZone.getDefault().id))
        m.put("_os_version", trunc(Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()))
        m.put("_device_model", trunc(Build.MODEL?.ifBlank { null } ?: "unknown"))
        val manu = Build.MANUFACTURER?.trim()?.ifBlank { null } ?: "unknown"
        m.put("_manufacturer", trunc(manu))
        return m
    }

    private fun deviceType(context: Context): String {
        val cfg = context.resources.configuration
        val layout = cfg.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val largeOrXl = layout >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val sw600 = cfg.smallestScreenWidthDp >= 600
        return if (largeOrXl || sw600) "tablet" else "mobile"
    }
}
