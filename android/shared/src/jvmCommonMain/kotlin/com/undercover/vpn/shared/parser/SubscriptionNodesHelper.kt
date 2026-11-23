package com.undercover.vpn.shared.parser

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SubscriptionNode(
    val tag: String,
    val type: String,
    val server: String = "",
    val port: Int = 0,
    val core: String = "SING-BOX",
) {
    val requiresXray: Boolean get() = core == "XRAY"
}

data class ProfileNodesInfo(
    val selectorTag: String? = null,
    val selectedNodeTag: String? = null,
    val nodes: List<SubscriptionNode> = emptyList(),
)

object SubscriptionNodesHelper {

    private val IGNORED_TYPES = setOf("direct", "block", "dns", "selector", "urltest")
    private val IGNORED_TAGS = setOf("direct", "direct-out", "block", "block-out", "dns", "dns-out")

    fun xraySidecarFile(configPath: String): File = File("$configPath.xray.json")

    fun saveXraySidecar(configPath: String, xrayConfigsByTag: Map<String, String>) {
        val file = xraySidecarFile(configPath)
        if (xrayConfigsByTag.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        val root = JSONObject()
        for ((tag, xrayJson) in xrayConfigsByTag) {
            root.put(tag, xrayJson)
        }
        file.writeText(root.toString())
    }

    fun loadXrayMap(configPath: String?): Map<String, String> {
        if (configPath.isNullOrBlank()) return emptyMap()
        val file = xraySidecarFile(configPath)
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val map = LinkedHashMap<String, String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valStr = root.optString(key)
                if (valStr.isNotEmpty()) {
                    map[key] = valStr
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun loadXrayConfigForNode(configPath: String?, nodeTag: String): String? {
        return loadXrayMap(configPath)[nodeTag]
    }

    /**
     * Checks whether the currently active outbound of [configPath] requires xray-core,
     * and if so returns the full Xray JSON configuration to run on 127.0.0.1:10808.
     */
    fun resolveActiveXrayConfig(configPath: String): String? {
        val file = File(configPath)
        if (!file.exists()) return null
        val xrayMap = loadXrayMap(configPath)
        if (xrayMap.isEmpty()) return null
        val info = extractNodesInfo(file.readText(), configPath)
        val activeTag = info.selectedNodeTag ?: "proxy"
        return xrayMap[activeTag] ?: xrayMap["proxy"]
    }

    fun extractNodesInfo(configJson: String, configPath: String? = null): ProfileNodesInfo {
        if (configJson.isBlank()) return ProfileNodesInfo()
        val xrayMap = loadXrayMap(configPath)
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

                        nodes.add(buildNodeInfo(memberTag, target, targetType, xrayMap[memberTag]))
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
                fallbackNodes.add(buildNodeInfo(tag, ob, type, xrayMap[tag]))
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

    private fun buildNodeInfo(
        tag: String,
        target: JSONObject?,
        targetType: String,
        xrayConfigJson: String?,
    ): SubscriptionNode {
        if (xrayConfigJson != null) {
            val xrayDetails = extractDetailsFromXrayJson(xrayConfigJson)
            return SubscriptionNode(
                tag = tag,
                type = xrayDetails.first,
                server = xrayDetails.second,
                port = xrayDetails.third,
                core = "XRAY",
            )
        }
        val displayType = when (targetType) {
            "urltest" -> "AUTO"
            "selector" -> "GROUP"
            "" -> "NODE"
            else -> targetType.uppercase()
        }
        return SubscriptionNode(
            tag = tag,
            type = displayType,
            server = target?.optString("server") ?: "",
            port = target?.optInt("server_port", 0) ?: 0,
            core = "SING-BOX",
        )
    }

    private fun extractDetailsFromXrayJson(xrayJson: String): Triple<String, String, Int> {
        return try {
            val root = JSONObject(xrayJson)
            val outbound = root.optJSONArray("outbounds")?.optJSONObject(0)
                ?: return Triple("XRAY", "", 0)
            val network = outbound.optJSONObject("streamSettings")
                ?.optString("network")?.uppercase()?.takeIf { it.isNotEmpty() }
                ?: outbound.optString("protocol").uppercase().ifEmpty { "XRAY" }
            val settings = outbound.optJSONObject("settings")
            val vnext = settings?.optJSONArray("vnext")?.optJSONObject(0)
            val serverObj = settings?.optJSONArray("servers")?.optJSONObject(0)
            val server = vnext?.optString("address") ?: serverObj?.optString("address") ?: ""
            val port = vnext?.optInt("port", 0) ?: serverObj?.optInt("port", 0) ?: 0
            Triple(network, server, port)
        } catch (_: Exception) {
            Triple("XRAY", "", 0)
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
