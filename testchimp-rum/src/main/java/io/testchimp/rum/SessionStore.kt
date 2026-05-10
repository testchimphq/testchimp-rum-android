package io.testchimp.rum

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.SecureRandom

internal class SessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrCreateSessionId(
        proposed: String?,
        sessionMetadata: JSONObject?,
        inactivityMs: Long,
    ): Pair<String, Boolean> {
        if (!proposed.isNullOrEmpty()) {
            persistSession(proposed, sessionMetadata)
            return Pair(proposed, false)
        }
        val nowMs = System.currentTimeMillis()
        val sid = prefs.getString(KEY_SESSION_ID, null)
        val lastMs = prefs.getLong(KEY_LAST_ACTIVITY, 0L)
        if (sid != null && lastMs > 0 && nowMs - lastMs < inactivityMs) {
            touchActivity()
            return Pair(sid, false)
        }
        val id = newSessionId()
        persistSession(id, sessionMetadata)
        return Pair(id, true)
    }

    fun touchActivity() {
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }

    fun eventCount(): Int = prefs.getInt(KEY_EVENT_COUNT, 0)

    fun setEventCount(n: Int) {
        prefs.edit().putInt(KEY_EVENT_COUNT, n).apply()
    }

    fun eventTypeCounts(): MutableMap<String, Int> {
        val s = prefs.getString(KEY_EVENT_TYPE_COUNTS, null) ?: return mutableMapOf()
        return try {
            val o = JSONObject(s)
            val out = mutableMapOf<String, Int>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.optInt(k, 0)
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun setEventTypeCounts(counts: Map<String, Int>) {
        val o = JSONObject()
        for ((k, v) in counts) o.put(k, v)
        prefs.edit().putString(KEY_EVENT_TYPE_COUNTS, o.toString()).apply()
    }

    fun sessionMetadata(): JSONObject? {
        val s = prefs.getString(KEY_SESSION_METADATA, null) ?: return null
        return try {
            JSONObject(s)
        } catch (_: Exception) {
            null
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun persistSession(id: String, metadata: JSONObject?) {
        val ed = prefs.edit()
            .putString(KEY_SESSION_ID, id)
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putInt(KEY_EVENT_COUNT, 0)
            .putString(KEY_EVENT_TYPE_COUNTS, JSONObject().toString())
        if (metadata != null) {
            ed.putString(KEY_SESSION_METADATA, metadata.toString())
        } else {
            ed.remove(KEY_SESSION_METADATA)
        }
        ed.apply()
    }

    private companion object {
        const val PREFS_NAME = "testchimp_rum"
        const val KEY_SESSION_ID = "testchimp_session_id"
        const val KEY_LAST_ACTIVITY = "testchimp_last_activity"
        const val KEY_EVENT_COUNT = "testchimp_event_count"
        const val KEY_EVENT_TYPE_COUNTS = "testchimp_event_type_counts"
        const val KEY_SESSION_METADATA = "testchimp_session_metadata"

        private val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        private fun newSessionId(): String {
            var t = System.currentTimeMillis()
            val timePart = StringBuilder()
            repeat(10) {
                timePart.insert(0, CROCKFORD[(t % 32L).toInt()])
                t /= 32L
            }
            val rnd = ByteArray(16)
            SecureRandom().nextBytes(rnd)
            val randPart = StringBuilder()
            for (b in rnd) {
                randPart.append(CROCKFORD[(b.toInt() and 0xFF) % 32])
            }
            return timePart.toString() + randPart.toString()
        }
    }
}
