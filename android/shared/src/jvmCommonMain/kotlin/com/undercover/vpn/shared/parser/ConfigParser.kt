package com.undercover.vpn.shared.parser

import com.undercover.vpn.shared.parser.happ.HappDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

data class ParsedProxyConfig(
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val outboundJson: Map<String, Any>
)

data class MultiCoreConfigResult(
    val singBoxJson: String,
    val xrayConfigsByTag: Map<String, String> = emptyMap(),
)

object ConfigParser {
    
    fun isSupportedUri(uriString: String): Boolean {
        val trimmed = uriString.trim()
        if (HappDecoder.isHappOrV2Link(trimmed)) {
            val decrypted = HappDecoder.decrypt(trimmed) ?: return false
            return isSupportedUri(decrypted)
        }
        return trimmed.startsWith("vless://") ||
                trimmed.startsWith("vmess://") ||
                trimmed.startsWith("ss://") ||
                trimmed.startsWith("trojan://") ||
                trimmed.startsWith("hysteria2://") ||
                trimmed.startsWith("hy2://")
    }

    fun parseUriToSingBoxJson(uriString: String): String? =
        parseUriWithXraySupport(uriString)?.singBoxJson

    fun parseUriWithXraySupport(uriString: String): MultiCoreConfigResult? {
        val effectiveUri = if (HappDecoder.isHappOrV2Link(uriString)) {
            HappDecoder.decrypt(uriString) ?: return null
        } else {
            uriString
        }
        if (XrayConfigGenerator.requiresXrayCore(effectiveUri)) {
            val xrayConfig = XrayConfigGenerator.generateXrayConfigFromUri(effectiveUri) ?: return null
            val outbounds = JSONArray().apply {
                put(buildXrayBridgeSocksOutbound("proxy"))
                put(JSONObject(mapOf("type" to "direct", "tag" to "direct-out")))
            }
            return MultiCoreConfigResult(
                singBoxJson = buildSingBoxJson(outbounds),
                xrayConfigsByTag = mapOf("proxy" to xrayConfig),
            )
        }
        val parsed = parseUri(effectiveUri) ?: return null
        return MultiCoreConfigResult(singBoxJson = generateSingBoxJson(parsed))
    }

    fun parseUriToName(uriString: String): String? {
        val effectiveUri = if (HappDecoder.isHappOrV2Link(uriString)) {
            HappDecoder.decrypt(uriString) ?: return null
        } else {
            uriString
        }
        return parseUri(effectiveUri)?.name
    }

    private fun parseUri(uriString: String): ParsedProxyConfig? {
        val trimmed = uriString.trim()
        if (trimmed.isEmpty()) return null

        return try {
            when {
                trimmed.startsWith("vless://") -> parseVless(trimmed)
                trimmed.startsWith("vmess://") -> parseVmess(trimmed)
                trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
                trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
                trimmed.startsWith("hysteria2://") || trimmed.startsWith("hy2://") -> parseHysteria2(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class UriParts(
        val scheme: String,
        val userInfo: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String
    )

    private fun splitUri(uriStr: String): UriParts? {
        val trimmed = uriStr.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd == -1) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        val afterScheme = trimmed.substring(schemeEnd + 3)

        val hashIdx = afterScheme.indexOf('#')
        val mainPart = if (hashIdx != -1) afterScheme.substring(0, hashIdx) else afterScheme
        val rawFragment = if (hashIdx != -1) afterScheme.substring(hashIdx + 1) else ""
        val fragment = if (rawFragment.isNotEmpty()) {
            try {
                URLDecoder.decode(rawFragment, "UTF-8")
            } catch (_: Exception) {
                rawFragment
            }
        } else ""

        val questionIdx = mainPart.indexOf('?')
        val authAndHost = if (questionIdx != -1) mainPart.substring(0, questionIdx) else mainPart
        val rawQuery = if (questionIdx != -1) mainPart.substring(questionIdx + 1) else ""
        val query = getQueryMap(rawQuery)

        val atIdx = authAndHost.lastIndexOf('@')
        val userInfo = if (atIdx != -1) authAndHost.substring(0, atIdx) else ""
        val hostAndPort = if (atIdx != -1) authAndHost.substring(atIdx + 1) else authAndHost

        val colonIdx = hostAndPort.lastIndexOf(':')
        val host: String
        val port: Int
        if (colonIdx != -1 && !hostAndPort.endsWith("]")) {
            host = hostAndPort.substring(0, colonIdx).removeSurrounding("[", "]")
            port = hostAndPort.substring(colonIdx + 1).toIntOrNull() ?: 443
        } else {
            host = hostAndPort.removeSurrounding("[", "]")
            port = 443
        }

        return UriParts(scheme, userInfo, host, port, query, fragment)
    }

    private fun parseVless(uriStr: String): ParsedProxyConfig? {
        val parts = splitUri(uriStr) ?: return null
        val uuid = parts.userInfo
        val server = parts.host
        val port = parts.port
        val name = parts.fragment.ifEmpty { "$server:$port" }
        val query = parts.query
        
        val outbound = mutableMapOf<String, Any>(
            "type" to "vless",
            "tag" to "proxy",
            "server" to server,
            "server_port" to port,
            "uuid" to uuid
        )

        query["flow"]?.takeIf { it.isNotEmpty() }?.let {
            outbound["flow"] = it
        }

        val security = query["security"] ?: ""
        if (security == "tls" || security == "reality") {
            val tls = mutableMapOf<String, Any>(
                "enabled" to true,
                "server_name" to (query["sni"]?.takeIf { it.isNotEmpty() } ?: server),
                "utls" to mapOf(
                    "enabled" to true,
                    "fingerprint" to (query["fp"]?.takeIf { it.isNotEmpty() } ?: "chrome")
                )
            )
            
            if (security == "reality") {
                tls["reality"] = mapOf(
                    "enabled" to true,
                    "public_key" to (query["pbk"] ?: ""),
                    "short_id" to (query["sid"] ?: "")
                )
            }
            outbound["tls"] = tls
        }

        val type = query["type"]?.takeIf { it.isNotEmpty() } ?: "tcp"
        if (type == "ws") {
            val headers = mutableMapOf<String, String>()
            query["host"]?.takeIf { it.isNotEmpty() }?.let { headers["Host"] = it }
            
            outbound["transport"] = mapOf(
                "type" to "ws",
                "path" to (query["path"]?.takeIf { it.isNotEmpty() } ?: "/"),
                "headers" to headers
            )
        } else if (type == "grpc") {
            outbound["transport"] = mapOf(
                "type" to "grpc",
                "service_name" to (query["serviceName"] ?: "")
            )
        }

        return ParsedProxyConfig(name, "VLESS", server, port, outbound)
    }

    private fun decodeBase64(str: String): ByteArray {
        val clean = str.trim().replace('-', '+').replace('_', '/').let {
            when (it.length % 4) {
                2 -> "$it=="
                3 -> "$it="
                else -> it
            }
        }
        return Base64.getDecoder().decode(clean)
    }

    private fun parseVmess(uriStr: String): ParsedProxyConfig? {
        val rawBase64 = uriStr.substring("vmess://".length)
        val decodedBytes = decodeBase64(rawBase64)
        val jsonStr = String(decodedBytes, Charsets.UTF_8)
        val map = JSONObject(jsonStr)

        val name = map.optString("ps", "VMess Server")
        val server = map.optString("add", "")
        val port = map.optInt("port", 443)
        val uuid = map.optString("id", "")

        val outbound = mutableMapOf<String, Any>(
            "type" to "vmess",
            "tag" to "proxy",
            "server" to server,
            "server_port" to port,
            "uuid" to uuid,
            "security" to map.optString("scy", "auto")
        )

        if (map.optString("tls") == "tls") {
            outbound["tls"] = mapOf(
                "enabled" to true,
                "server_name" to map.optString("sni", map.optString("host", server))
            )
        }

        return ParsedProxyConfig(name, "VMess", server, port, outbound)
    }

    private fun parseShadowsocks(uriStr: String): ParsedProxyConfig? {
        val parts = splitUri(uriStr) ?: return null
        val name = parts.fragment.ifEmpty { "Shadowsocks" }
        var method = "aes-128-gcm"
        var password = ""
        val server = parts.host
        val port = parts.port

        val userInfo = parts.userInfo
        if (userInfo.isNotEmpty()) {
            if (userInfo.contains(":")) {
                val subParts = userInfo.split(":", limit = 2)
                method = subParts[0]
                password = subParts[1]
            } else {
                val decoded = String(decodeBase64(userInfo), Charsets.UTF_8)
                if (decoded.contains(":")) {
                    val subParts = decoded.split(":", limit = 2)
                    method = subParts[0]
                    password = subParts[1]
                }
            }
        }

        val outbound = mapOf<String, Any>(
            "type" to "shadowsocks",
            "tag" to "proxy",
            "server" to server,
            "server_port" to port,
            "method" to method,
            "password" to password
        )

        return ParsedProxyConfig(name, "Shadowsocks", server, port, outbound)
    }

    private fun parseTrojan(uriStr: String): ParsedProxyConfig? {
        val parts = splitUri(uriStr) ?: return null
        val password = parts.userInfo
        val server = parts.host
        val port = parts.port
        val name = parts.fragment.ifEmpty { "Trojan" }
        val query = parts.query

        val outbound = mapOf<String, Any>(
            "type" to "trojan",
            "tag" to "proxy",
            "server" to server,
            "server_port" to port,
            "password" to password,
            "tls" to mapOf(
                "enabled" to true,
                "server_name" to (query["sni"] ?: server)
            )
        )

        return ParsedProxyConfig(name, "Trojan", server, port, outbound)
    }

    private fun parseHysteria2(uriStr: String): ParsedProxyConfig? {
        val parts = splitUri(uriStr) ?: return null
        val password = parts.userInfo
        val server = parts.host
        val port = parts.port
        val name = parts.fragment.ifEmpty { "Hysteria2" }
        val query = parts.query

        val outbound = mapOf<String, Any>(
            "type" to "hysteria2",
            "tag" to "proxy",
            "server" to server,
            "server_port" to port,
            "password" to password,
            "tls" to mapOf(
                "enabled" to true,
                "server_name" to (query["sni"] ?: server),
                "insecure" to (query["insecure"] == "1")
            )
        )

        return ParsedProxyConfig(name, "Hysteria2", server, port, outbound)
    }

    private fun getQueryMap(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = URLDecoder.decode(parts[1], "UTF-8")
            } else if (parts.size == 1) {
                map[parts[0]] = ""
            }
        }
        return map
    }

    /**
     * Builds a complete sing-box configuration from a list of proxy share links
     * (the format most subscription servers return after base64 decoding).
     * Nodes are wrapped in a "proxy" selector so the route can pick one of them.
     */
    fun parseUriListToSingBoxJson(uriStrings: List<String>): String? =
        parseUriListWithXraySupport(uriStrings)?.singBoxJson

    fun parseUriListWithXraySupport(uriStrings: List<String>): MultiCoreConfigResult? {
        val outbounds = JSONArray()
        val nodeTags = mutableListOf<String>()
        val usedTags = mutableSetOf<String>()
        val xrayConfigsByTag = LinkedHashMap<String, String>()
        val nodeOutboundObjects = mutableListOf<JSONObject>()

        uriStrings.forEachIndexed { index, raw ->
            val uri = raw.trim()
            if (uri.isEmpty()) return@forEachIndexed
            val effectiveUri = if (HappDecoder.isHappOrV2Link(uri)) {
                HappDecoder.decrypt(uri) ?: return@forEachIndexed
            } else {
                uri
            }

            if (XrayConfigGenerator.requiresXrayCore(effectiveUri)) {
                val xrayConfig = XrayConfigGenerator.generateXrayConfigFromUri(effectiveUri) ?: return@forEachIndexed
                val baseName = extractRawFragmentName(effectiveUri)?.ifBlank { null }
                    ?: "Node ${index + 1}"
                val tag = deduplicateTag(baseName, usedTags)
                nodeTags.add(tag)
                xrayConfigsByTag[tag] = xrayConfig
                nodeOutboundObjects.add(buildXrayBridgeSocksOutbound(tag))
            } else {
                val parsed = parseUri(effectiveUri) ?: return@forEachIndexed
                val base = parsed.name.ifBlank { "Node ${index + 1}" }
                val tag = deduplicateTag(base, usedTags)
                nodeTags.add(tag)
                val outboundObj = JSONObject(parsed.outboundJson)
                outboundObj.put("tag", tag)
                nodeOutboundObjects.add(outboundObj)
            }
        }

        if (nodeTags.isEmpty()) return null

        outbounds.put(
            JSONObject(
                mapOf(
                    "type" to "selector",
                    "tag" to "proxy",
                    "outbounds" to JSONArray().also { tags -> nodeTags.forEach { tags.put(it) } },
                    "default" to nodeTags.first(),
                    "interrupt_exist_connections" to true,
                ),
            ),
        )
        nodeOutboundObjects.forEach { outbounds.put(it) }
        outbounds.put(JSONObject(mapOf("type" to "direct", "tag" to "direct-out")))

        return MultiCoreConfigResult(
            singBoxJson = buildSingBoxJson(outbounds),
            xrayConfigsByTag = xrayConfigsByTag,
        )
    }

    private fun deduplicateTag(base: String, usedTags: MutableSet<String>): String {
        var tag = base
        var suffix = 2
        while (!usedTags.add(tag)) {
            tag = "$base ($suffix)"
            suffix++
        }
        return tag
    }

    private fun extractRawFragmentName(uri: String): String? {
        val hashIdx = uri.indexOf('#')
        if (hashIdx == -1) return null
        val raw = uri.substring(hashIdx + 1).trim()
        return if (raw.isEmpty()) null else try {
            URLDecoder.decode(raw, "UTF-8").trim()
        } catch (_: Exception) {
            raw
        }
    }

    private fun buildXrayBridgeSocksOutbound(tag: String): JSONObject = JSONObject(
        mapOf(
            "type" to "socks",
            "tag" to tag,
            "server" to XrayConfigGenerator.LOCAL_SOCKS_HOST,
            "server_port" to XrayConfigGenerator.LOCAL_SOCKS_PORT,
            "version" to "5",
        ),
    )

    private fun generateSingBoxJson(config: ParsedProxyConfig): String {
        val outbounds = JSONArray()
        // Ensure the outbound uses the tag 'proxy' which we use as the final route
        val outboundObj = JSONObject(config.outboundJson)
        outboundObj.put("tag", "proxy")
        outbounds.put(outboundObj)
        
        outbounds.put(JSONObject(mapOf("type" to "direct", "tag" to "direct-out")))

        return buildSingBoxJson(outbounds)
    }

    private fun buildSingBoxJson(outbounds: JSONArray): String {
        val remoteDns = JSONObject(mapOf(
            "tag" to "dns-remote",
            "type" to "https",
            "server" to "1.1.1.1",
            "detour" to "proxy",
        ))
        val localDns = JSONObject(mapOf(
            "tag" to "dns-local",
            "type" to "local",
        ))

        val dns = JSONObject()
        dns.put("servers", JSONArray().put(remoteDns).put(localDns))
        dns.put("final", "dns-remote")
        dns.put("strategy", "prefer_ipv4")

        val inbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "tun0")
            put("mtu", 9000)
            put("auto_route", true)
            put("auto_redirect", false)
            put("stack", "gvisor")
            put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
        }

        val inbounds = JSONArray().put(inbound)

        val routeRules = JSONArray()
        routeRules.put(JSONObject(mapOf(
            "action" to "sniff",
        )))

        routeRules.put(JSONObject(mapOf(
            "protocol" to "dns",
            "action" to "hijack-dns",
        )))

        routeRules.put(JSONObject(mapOf(
            "ip_is_private" to true,
            "outbound" to "direct-out",
        )))

        val route = JSONObject()
        route.put("default_domain_resolver", "dns-local")
        route.put("auto_detect_interface", true)
        route.put("rules", routeRules)
        route.put("final", "proxy")

        val log = JSONObject(mapOf(
            "level" to "info",
            "timestamp" to true,
        ))

        val fullConfig = JSONObject()
        fullConfig.put("log", log)
        fullConfig.put("dns", dns)
        fullConfig.put("inbounds", inbounds)
        fullConfig.put("outbounds", outbounds)
        fullConfig.put("route", route)

        return fullConfig.toString(2)
    }
}
