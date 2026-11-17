package io.github.tancan1998.xviewtv.core.network

import org.json.JSONObject

/** X can deliver a final object, NDJSON, or SSE deltas. Never cache the first token only. */
internal object WebTranslationParser {
    fun text(raw: String): String? {
        val chunks = raw.lineSequence().map { it.trim().removePrefix("data:").trim() }
            .filter { it.startsWith("{") }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList()
        val objects = if (chunks.isEmpty()) listOfNotNull(runCatching { JSONObject(raw) }.getOrNull()) else chunks
        var result = ""
        for (item in objects) {
            val data = item.optJSONObject("result") ?: item
            val token = data.optString("text", "").ifBlank { data.optString("translation", "") }
            if (token.isEmpty()) continue
            result = when {
                result.isEmpty() || (token.length > 1 && token.startsWith(result)) -> token
                result.startsWith(token) && token.length > 1 -> result
                else -> result + token
            }
            if (result.length > 30000) return result.take(30000)
        }
        return result.takeIf { it.isNotBlank() }
    }
}
