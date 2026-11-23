package com.undercover.vpn.shared.parser

import org.json.JSONArray
import org.json.JSONObject

data class SubscriptionNode(
    val tag: String,
    val type: String,
    val server: String = "",
    val port: Int = 0,
)

data class ProfileNodesInfo(
    val selectorTag: String? = null,
    val selectedNodeTag: String? = null,
    val nodes: List<SubscriptionNode> = emptyList(),
)

object SubscriptionNodesHelper {

    private val IGNORED_TYPES = setOf("direct", "block", "dns", "selector", "urltest")
    private val IGNORED_TAGS = setOf("direct", "direct-out", "block", "block-out", "dns", "dns-out")

    fun extractNodesInfo(configJson: String): ProfileNodesInfo {
        if (configJson.isBlank()) return ProfileNodesInfo()
        return try {
            val root = JSONObject(configJson)
            val outbounds = root.optJSONArray("outbounds") ?: return ProfileNodesInfo()

            val outboundByTag = LinkedHashMap<String, JSONObject>()
            val selectors = mutableListOf<JSONObject>()
            var firstUrlTest: JSONObject? = null

            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val tag = ob.optString("tag")
                val type = ob.optString("type").lowercase()
                if (tag.isNotEmpty()) {
                    outboundByTag[tag] = ob
                }
                if (type == "selector") {
                    selectors.add(ob)
                } else if (type == "urltest" && firstUrlTest == null) {
                    firstUrlTest = ob
                }
            }

            // Prefer the selector referenced by route.final, otherwise the first selector
            val routeFinal = root.optJSONObject("route")?.optString("final")?.takeIf { it.isNotEmpty() }
            val primarySelector = selectors.firstOrNull { it.optString("tag") == routeFinal }
                ?: selectors.firstOrNull()

            if (primarySelector != null) {
                val selectorTag = primarySelector.optString("tag").takeIf { it.isNotEmpty() }
                val memberTags = primarySelector.optJSONArray("outbounds")
                val nodes = mutableListOf<SubscriptionNode>()

                if (memberTags != null) {
                    for (i in 0 until memberTags.length()) {
                        val memberTag = memberTags.optString(i)
                        if (memberTag.isBlank() || memberTag.lowercase() in IGNORED_TAGS) continue
                        val target = outboundByTag[memberTag]
                        val targetType = target?.optString("type")?.lowercase() ?: ""
                        if (targetType in setOf("direct", "block", "dns")) continue

                        val displayType = when (targetType) {
                            "urltest" -> "AUTO"
                            "selector" -> "GROUP"
                            "" -> "NODE"
                            else -> targetType.uppercase()
                        }
                        nodes.add(
                            SubscriptionNode(
                                tag = memberTag,
                                type = displayType,
                                server = target?.optString("server") ?: "",
                                port = target?.optInt("server_port", 0) ?: 0,
                            )
                        )
                    }
                }

                val realNodesCount = nodes.count { it.type != "AUTO" && it.type != "GROUP" }
                val filteredNodes = if (realNodesCount <= 1) {
                    nodes.filter { it.type != "AUTO" }
                } else {
                    nodes
                }

                val rawDefault = primarySelector.optString("default").takeIf { it.isNotEmpty() }
                val defaultTag = rawDefault?.takeIf { tag -> filteredNodes.any { it.tag == tag } }
                    ?: filteredNodes.firstOrNull()?.tag

                return ProfileNodesInfo(
                    selectorTag = selectorTag,
                    selectedNodeTag = defaultTag,
                    nodes = filteredNodes,
                )
            }

            // Fallback: no selector, check urltest or direct proxy outbounds
            val fallbackNodes = mutableListOf<SubscriptionNode>()
            for ((tag, ob) in outboundByTag) {
                val type = ob.optString("type").lowercase()
                if (type in IGNORED_TYPES || tag.lowercase() in IGNORED_TAGS) continue
                fallbackNodes.add(
                    SubscriptionNode(
                        tag = tag,
                        type = type.uppercase(),
                        server = ob.optString("server"),
                        port = ob.optInt("server_port", 0),
                    )
                )
            }

            val selectedTag = routeFinal?.takeIf { tag -> fallbackNodes.any { it.tag == tag } }
                ?: fallbackNodes.firstOrNull()?.tag

            ProfileNodesInfo(
                selectorTag = firstUrlTest?.optString("tag")?.takeIf { it.isNotEmpty() },
                selectedNodeTag = selectedTag,
                nodes = fallbackNodes,
            )
        } catch (_: Exception) {
            ProfileNodesInfo()
        }
    }

    fun selectNodeInConfig(configJson: String, selectorTag: String, nodeTag: String): String? {
        return try {
            val root = JSONObject(configJson)
            val outbounds = root.optJSONArray("outbounds") ?: return null
            var updated = false
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                if (ob.optString("type").equals("selector", ignoreCase = true) &&
                    ob.optString("tag") == selectorTag
                ) {
                    val members = ob.optJSONArray("outbounds") ?: JSONArray()
                    var contains = false
                    for (j in 0 until members.length()) {
                        if (members.optString(j) == nodeTag) {
                            contains = true
                            break
                        }
                    }
                    if (contains) {
                        ob.put("default", nodeTag)
                        updated = true
                    }
                    break
                }
            }
            if (updated) root.toString(2) else null
        } catch (_: Exception) {
            null
        }
    }
}
