package io.testchimp.rum

import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON-serializable metadata (mirrors @testchimp/rum-js `D()`). */
internal object RumValidation {
    const val MAX_METADATA_KEYS = 10
    const val MAX_KEY_LENGTH = 50
    const val MAX_STRING_VALUE_LENGTH = 200
    const val MAX_METADATA_ARRAY_LENGTH = 50
    const val MAX_TITLE_LENGTH = 100
    const val MAX_EVENT_PAYLOAD_BYTES = 5120

    /** Normalize from Kotlin map with List values */
    fun normalizeMetadataFromMap(raw: Map<String, Any>?): JSONObject? {
        if (raw.isNullOrEmpty()) return null
        if (raw.size > MAX_METADATA_KEYS) return null
        val out = JSONObject()
        for ((k, v) in raw) {
            if (k.length > MAX_KEY_LENGTH) return null
            when (v) {
                is String -> {
                    if (v.length > MAX_STRING_VALUE_LENGTH) return null
                    out.put(k, v)
                }
                is Number -> out.put(k, v)
                is Boolean -> out.put(k, v)
                is List<*> -> {
                    if (v.size > MAX_METADATA_ARRAY_LENGTH) return null
                    val arr = JSONArray()
                    for (item in v) {
                        when (item) {
                            is String -> {
                                if (item.length > MAX_STRING_VALUE_LENGTH) return null
                                arr.put(item)
                            }
                            is Number -> arr.put(item)
                            is Boolean -> arr.put(item)
                            null -> arr.put(JSONObject.NULL)
                            else -> return null
                        }
                    }
                    out.put(k, arr)
                }
                else -> return null
            }
        }
        return if (out.length() == 0) null else out
    }

    fun buildEmitPayload(title: String, metadata: Map<String, Any>?): ByteArray? {
        if (title.isEmpty() || title.length > MAX_TITLE_LENGTH) return null
        val obj = JSONObject()
        obj.put("title", title)
        obj.put("timestampMillis", System.currentTimeMillis())
        val meta = normalizeMetadataFromMap(metadata)
        if (meta != null) obj.put("metadata", meta)
        val data = obj.toString().toByteArray(Charsets.UTF_8)
        return if (data.size <= MAX_EVENT_PAYLOAD_BYTES) data else null
    }
}
