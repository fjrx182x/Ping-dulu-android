package com.example.data.remote.dto

import com.example.domain.model.FieldTechnician
import com.example.domain.model.OfficialLabel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Crash-proof parser for GET /api/users and GET /api/labels.
 * Supports both bare JSON arrays `[...]` and wrapped objects (`{ "data": [...] }`,
 * `{ "users": [...] }`, `{ "labels": [...] }`) without throwing on null or extra fields.
 */
object CatalogResponseParser {

    fun parseUsersJson(rawJson: String): List<FieldTechnician> {
        val array = extractJsonArray(rawJson, wrapperKeys = listOf("users", "data", "items")) ?: return emptyList()
        val result = mutableListOf<FieldTechnician>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            if (id <= 0L) continue
            val username = obj.optCleanString("username")
            val displayName = obj.optCleanString("display_name")
                .ifBlank { obj.optCleanString("name") }
                .ifBlank { username }
                .ifBlank { "Teknisi #$id" }
            val role = obj.optCleanString("role").ifBlank { "technician" }
            result += FieldTechnician(
                id = id,
                username = username,
                displayName = displayName,
                role = role
            )
        }
        return result.distinctBy { it.id }
    }

    fun parseLabelsJson(rawJson: String): List<OfficialLabel> {
        val array = extractJsonArray(rawJson, wrapperKeys = listOf("labels", "data", "items")) ?: return emptyList()
        val result = mutableListOf<OfficialLabel>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            val name = obj.optCleanString("name")
                .ifBlank { obj.optCleanString("label") }
                .ifBlank { obj.optCleanString("title") }
            if (name.isBlank()) continue
            val rawColor = obj.optCleanString("color")
                .ifBlank { obj.optCleanString("color_hex") }
                .ifBlank { obj.optCleanString("hex") }
                .ifBlank { "#36459B" }
            val normalizedColor = if (rawColor.startsWith("#")) rawColor else "#$rawColor"
            result += OfficialLabel(
                id = if (id > 0L) id else -(i + 1L),
                name = name,
                colorHex = normalizedColor
            )
        }
        return result.distinctBy { it.name.lowercase() }
    }

    private fun extractJsonArray(rawJson: String, wrapperKeys: List<String>): JSONArray? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                wrapperKeys.firstNotNullOfOrNull { key -> root.optJSONArray(key) }
            }
        }.getOrNull()
    }

    private fun JSONObject.optCleanString(key: String): String {
        if (isNull(key)) return ""
        return optString(key, "").trim()
    }
}
