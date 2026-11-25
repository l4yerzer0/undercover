package com.undercover.vpn.shared.parser

import com.undercover.vpn.shared.parser.happ.HappDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

object XrayConfigGenerator {

    const val LOCAL_SOCKS_HOST = "127.0.0.1"
    const val LOCAL_SOCKS_PORT = 10808
    const val LOCAL_SOCKS_PORT_SECONDARY = 10809

    fun requiresXrayCore(uriString: String): Boolean {
        val effective = resolveEffectiveUri(uriString) ?: return false
        val lower = effective.lowercase()
        return lower.contains("type=xhttp") ||
            lower.contains("type=splithttp") ||
            lower.contains("type=kcp") ||
            lower.contains("type=mkcp")
    }

    fun detectNetworkLabel(uriString: String): String {
        val effective = resolveEffectiveUri(uriString) ?: return "XRAY"
        val queryPart = effective.substringAfter('?', "").substringBefore('#')
        val typeParam = queryPart.split('&')
            .firstOrNull { it.startsWith("type=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.uppercase()
        return when (typeParam) {
            "XHTTP", "SPLITHTTP" -> "XHTTP"
            "KCP", "MKCP" -> "KCP"
            else -> "XRAY"
        }
    }

    fun generateXrayConfigFromUri(
        uriString: String,
        socksPort: Int = LOCAL_SOCKS_PORT,
    ): String? {
        val effective = resolveEffectiveUri(uriString)?.trim() ?: return null
        val outbound = when {
            effective.startsWith("vless://", ignoreCase = true) -> buildVlessOutbound(effective)
            effective.startsWith("trojan://", ignoreCase = true) -> buildTrojanOutbound(effective)
            effective.startsWith("vmess://", ignoreCase = true) -> buildVmessOutbound(effective)
            effective.startsWith("ss://", ignoreCase = true) -> buildShadowsocksOutbound(effective)
            else -> null
        } ?: return null

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        val dns = JSONObject()
        dns.put("servers", JSONArray().put("8.8.8.8").put("77.88.88.88").put("1.1.1.1"))
        root.put("dns", dns)

        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", socksPort)
            put("listen", LOCAL_SOCKS_HOST)
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
        }
        root.put("inbounds", JSONArray().put(socksInbound))

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }
        root.put("outbounds", JSONArray().put(outbound).put(directOutbound))

        return root.toString(2)
    }

    fun adjustInboundPort(xrayJson: String, port: Int, tag: String): String {
        return try {
            val root = JSONObject(xrayJson)
            val inbounds = root.optJSONArray("inbounds")
            if (inbounds != null && inbounds.length() > 0) {
                val inb = inbounds.getJSONObject(0)
                inb.put("port", port)
                inb.put("tag", tag)
            }
            root.toString(2)
        } catch (_: Exception) {
            xrayJson
        }
    }

    fun combineXrayConfigs(
        primaryXrayJson: String?,
        secondaryXrayJson: String?,
    ): String? {
        val list = mutableListOf<Pair<Int, String>>()
        if (!primaryXrayJson.isNullOrBlank()) list.add(0 to primaryXrayJson)
        if (!secondaryXrayJson.isNullOrBlank()) list.add(1 to secondaryXrayJson)
        return combineMultiXrayConfigs(list)
    }

    /**
     * Combines any number of Xray configs into a single multi-inbound Xray configuration.
     * Each entry is (slotIndex, xrayJson) where slotIndex 0 -> port 10808, slotIndex 1 -> 10809, etc.
     */
    fun combineMultiXrayConfigs(indexedConfigs: List<Pair<Int, String>>): String? {
        if (indexedConfigs.isEmpty()) return null
        if (indexedConfigs.size == 1) {
            val (slotIndex, json) = indexedConfigs.first()
            return if (slotIndex == 0) {
                json
            } else {
                adjustInboundPort(json, LOCAL_SOCKS_PORT + slotIndex, "socks-$slotIndex")
            }
        }

        return try {
            val combined = JSONObject()
            combined.put("log", JSONObject().put("loglevel", "warning"))
            val dns = JSONObject().put("servers", JSONArray().put("8.8.8.8").put("77.88.88.88").put("1.1.1.1"))
            combined.put("dns", dns)

            val inbounds = JSONArray()
            val outbounds = JSONArray()
            val rules = JSONArray()

            for ((slotIndex, xrayJson) in indexedConfigs) {
                val root = JSONObject(xrayJson)
                val inboundTag = "socks-$slotIndex"
                val outboundTag = "out-$slotIndex"
                val port = LOCAL_SOCKS_PORT + slotIndex

                inbounds.put(
                    JSONObject().apply {
                        put("tag", inboundTag)
                        put("port", port)
                        put("listen", LOCAL_SOCKS_HOST)
                        put("protocol", "socks")
                        put("settings", JSONObject().put("auth", "noauth").put("udp", true))
                    },
                )

                val srcOutbounds = root.optJSONArray("outbounds") ?: JSONArray()
                val proxyOutbound = srcOutbounds.optJSONObject(0) ?: JSONObject()
                proxyOutbound.put("tag", outboundTag)
                outbounds.put(proxyOutbound)

                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", JSONArray().put(inboundTag))
                        .put("outboundTag", outboundTag),
                )
            }

            outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))

            combined.put("inbounds", inbounds)
            combined.put("outbounds", outbounds)
            combined.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))

            combined.toString(2)
        } catch (_: Exception) {
            indexedConfigs.firstOrNull()?.second
        }
    }

    private fun resolveEffectiveUri(uriString: String): String? {
        val trimmed = uriString.trim()
        return if (HappDecoder.isHappOrV2Link(trimmed)) {
            HappDecoder.decrypt(trimmed)
        } else {
            trimmed
        }
    }

    private data class ParsedUriParts(
        val userInfo: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
    )

    private fun splitUri(uriStr: String): ParsedUriParts? {
        val schemeEnd = uriStr.indexOf("://")
        if (schemeEnd == -1) return null
        val afterScheme = uriStr.substring(schemeEnd + 3).substringBefore('#')
        val mainPart = afterScheme.substringBefore('?')
        val rawQuery = afterScheme.substringAfter('?', "")

        val query = LinkedHashMap<String, String>()
        if (rawQuery.isNotEmpty()) {
            for (pair in rawQuery.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq == -1) {
                    query[decodeUrl(pair)] = ""
                } else {
                    query[decodeUrl(pair.substring(0, eq))] = decodeUrl(pair.substring(eq + 1))
                }
            }
        }

        val atIdx = mainPart.lastIndexOf('@')
        val userInfo = if (atIdx != -1) decodeUrl(mainPart.substring(0, atIdx)) else ""
        val hostAndPort = if (atIdx != -1) mainPart.substring(atIdx + 1) else mainPart

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
        if (host.isBlank()) return null
        return ParsedUriParts(userInfo, host, port, query)
    }

    private fun decodeUrl(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    private fun buildVlessOutbound(uriStr: String): JSONObject? {
        val parts = splitUri(uriStr) ?: return null
        val rawType = parts.query["type"]?.lowercase()?.ifEmpty { "tcp" } ?: "tcp"
        val user = JSONObject().apply {
            put("id", parts.userInfo)
            put("encryption", parts.query["encryption"]?.ifEmpty { "none" } ?: "none")
            if (rawType == "tcp" || rawType == "raw") {
                parts.query["flow"]?.takeIf { it.isNotEmpty() }?.let { put("flow", it) }
            }
        }
        val vnextItem = JSONObject().apply {
            put("address", parts.host)
            put("port", parts.port)
            put("users", JSONArray().put(user))
        }
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().put("vnext", JSONArray().put(vnextItem)))
            put("streamSettings", buildStreamSettings(parts.host, parts.query))
        }
    }

    private fun buildTrojanOutbound(uriStr: String): JSONObject? {
        val parts = splitUri(uriStr) ?: return null
        val serverObj = JSONObject().apply {
            put("address", parts.host)
            put("port", parts.port)
            put("password", parts.userInfo)
        }
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "trojan")
            put("settings", JSONObject().put("servers", JSONArray().put(serverObj)))
            put("streamSettings", buildStreamSettings(parts.host, parts.query))
        }
    }

    private fun buildShadowsocksOutbound(uriStr: String): JSONObject? {
        val parts = splitUri(uriStr) ?: return null
        var method = "aes-256-gcm"
        var password = parts.userInfo
        if (!password.contains(":")) {
            try {
                val decoded = String(Base64.getDecoder().decode(password.replace('-', '+').replace('_', '/')), Charsets.UTF_8)
                if (decoded.contains(":")) {
                    method = decoded.substringBefore(":")
                    password = decoded.substringAfter(":")
                }
            } catch (_: Exception) {
            }
        } else {
            method = password.substringBefore(":")
            password = password.substringAfter(":")
        }
        val serverObj = JSONObject().apply {
            put("address", parts.host)
            put("port", parts.port)
            put("method", method)
            put("password", password)
        }
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "shadowsocks")
            put("settings", JSONObject().put("servers", JSONArray().put(serverObj)))
            put("streamSettings", buildStreamSettings(parts.host, parts.query))
        }
    }

    private fun buildVmessOutbound(uriStr: String): JSONObject? {
        val b64 = uriStr.substringAfter("://").substringBefore('#').trim()
        val jsonStr = try {
            val clean = b64.replace('-', '+').replace('_', '/')
            val padded = when (clean.length % 4) {
                2 -> "$clean=="
                3 -> "$clean="
                else -> clean
            }
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val vmess = try {
            JSONObject(jsonStr)
        } catch (_: Exception) {
            return null
        }
        val host = vmess.optString("add")
        val port = vmess.optInt("port", 443)
        val uuid = vmess.optString("id")
        if (host.isBlank() || uuid.isBlank()) return null

        val user = JSONObject().apply {
            put("id", uuid)
            put("security", vmess.optString("scy").ifEmpty { "auto" })
        }
        val vnextItem = JSONObject().apply {
            put("address", host)
            put("port", port)
            put("users", JSONArray().put(user))
        }
        val query = LinkedHashMap<String, String>()
        query["type"] = vmess.optString("net", "tcp")
        query["security"] = vmess.optString("tls", "none")
        query["sni"] = vmess.optString("sni")
        query["host"] = vmess.optString("host")
        query["path"] = vmess.optString("path")
        query["fp"] = vmess.optString("fp")
        query["alpn"] = vmess.optString("alpn")

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vmess")
            put("settings", JSONObject().put("vnext", JSONArray().put(vnextItem)))
            put("streamSettings", buildStreamSettings(host, query))
        }
    }

    private fun buildStreamSettings(defaultHost: String, query: Map<String, String>): JSONObject {
        val stream = JSONObject()
        val rawType = query["type"]?.lowercase()?.ifEmpty { "tcp" } ?: "tcp"
        val network = when (rawType) {
            "splithttp" -> "xhttp"
            "mkcp" -> "kcp"
            else -> rawType
        }
        stream.put("network", network)

        val security = query["security"]?.lowercase()?.ifEmpty { "none" } ?: "none"
        stream.put("security", security)

        val sni = query["sni"]?.takeIf { it.isNotEmpty() }
            ?: query["host"]?.takeIf { it.isNotEmpty() }
            ?: defaultHost
        val fp = query["fp"]?.takeIf { it.isNotEmpty() } ?: "chrome"
        val alpnList = query["alpn"]?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

        when (security) {
            "tls" -> {
                val tls = JSONObject().apply {
                    put("serverName", sni)
                    put("fingerprint", fp)
                    if (!alpnList.isNullOrEmpty()) {
                        put("alpn", JSONArray(alpnList))
                    }
                    if (query["allowInsecure"] == "1" || query["allowInsecure"] == "true") {
                        put("allowInsecure", true)
                    }
                }
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                val reality = JSONObject().apply {
                    put("serverName", sni)
                    put("fingerprint", fp)
                    query["pbk"]?.takeIf { it.isNotEmpty() }?.let { put("publicKey", it) }
                    query["sid"]?.takeIf { it.isNotEmpty() }?.let { put("shortId", it) }
                    query["spx"]?.takeIf { it.isNotEmpty() }?.let { put("spiderX", it) }
                }
                stream.put("realitySettings", reality)
            }
        }

        when (network) {
            "xhttp" -> {
                val xhttp = JSONObject()
                query["path"]?.takeIf { it.isNotEmpty() }?.let { xhttp.put("path", it) }
                query["host"]?.takeIf { it.isNotEmpty() }?.let { xhttp.put("host", it) }
                query["mode"]?.takeIf { it.isNotEmpty() }?.let { xhttp.put("mode", it) }
                query["extra"]?.takeIf { it.isNotEmpty() }?.let { extraStr ->
                    try {
                        xhttp.put("extra", JSONObject(extraStr))
                    } catch (_: Exception) {
                    }
                }
                stream.put("xhttpSettings", xhttp)
            }
            "ws" -> {
                val ws = JSONObject()
                query["path"]?.takeIf { it.isNotEmpty() }?.let { ws.put("path", it) }
                query["host"]?.takeIf { it.isNotEmpty() }?.let {
                    ws.put("headers", JSONObject().put("Host", it))
                }
                stream.put("wsSettings", ws)
            }
            "grpc" -> {
                val grpc = JSONObject()
                query["serviceName"]?.takeIf { it.isNotEmpty() }?.let { grpc.put("serviceName", it) }
                if (query["mode"] == "multi") grpc.put("multiMode", true)
                stream.put("grpcSettings", grpc)
            }
            "kcp" -> {
                val kcp = JSONObject()
                query["headerType"]?.takeIf { it.isNotEmpty() }?.let {
                    kcp.put("header", JSONObject().put("type", it))
                }
                query["seed"]?.takeIf { it.isNotEmpty() }?.let { kcp.put("seed", it) }
                stream.put("kcpSettings", kcp)
            }
            "httpupgrade" -> {
                val hu = JSONObject()
                query["path"]?.takeIf { it.isNotEmpty() }?.let { hu.put("path", it) }
                query["host"]?.takeIf { it.isNotEmpty() }?.let { hu.put("host", it) }
                stream.put("httpupgradeSettings", hu)
            }
        }

        return stream
    }
}
