package com.undercover.vpn.database

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Parcelize
data class RoutingChannel(
    val id: String = UUID.randomUUID().toString().take(8),
    val serverId: Long = -1L,
    val nodeTag: String? = null,
    val apps: Set<String> = emptySet(),
    val domains: List<String> = emptyList(),
    val categories: Set<String> = emptySet(),
) : Parcelable {

    val ruleCount: Int
        get() = apps.size + domains.size + categories.size

    companion object {
        fun fromJsonArray(json: String?): List<RoutingChannel> {
            if (json.isNullOrBlank() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                val result = ArrayList<RoutingChannel>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString().take(8) }
                    val serverId = obj.optLong("serverId", -1L)
                    if (serverId <= 0L) continue
                    val nodeTag = obj.optString("nodeTag", "").takeIf { it.isNotBlank() }
                    val apps = UserProfile.jsonToStringList(obj.optJSONArray("apps")?.toString()).toSet()
                    val domains = UserProfile.jsonToStringList(obj.optJSONArray("domains")?.toString())
                    val categories = UserProfile.jsonToStringList(obj.optJSONArray("categories")?.toString()).toSet()
                    result.add(
                        RoutingChannel(
                            id = id,
                            serverId = serverId,
                            nodeTag = nodeTag,
                            apps = apps,
                            domains = domains,
                            categories = categories,
                        ),
                    )
                }
                result
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(channels: List<RoutingChannel>): String {
            if (channels.isEmpty()) return "[]"
            val arr = JSONArray()
            for (ch in channels) {
                val obj = JSONObject()
                obj.put("id", ch.id)
                obj.put("serverId", ch.serverId)
                if (!ch.nodeTag.isNullOrBlank()) obj.put("nodeTag", ch.nodeTag)
                obj.put("apps", JSONArray(ch.apps.toList()))
                obj.put("domains", JSONArray(ch.domains))
                obj.put("categories", JSONArray(ch.categories.toList()))
                arr.put(obj)
            }
            return arr.toString()
        }
    }
}
