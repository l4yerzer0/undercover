package com.undercover.vpn.desktop.core

import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.ProfileType
import com.undercover.vpn.desktop.model.ProxyNode
import com.undercover.vpn.shared.parser.ConfigParser
import com.undercover.vpn.shared.parser.SingBoxConfigMigrator
import com.undercover.vpn.shared.parser.SubscriptionMetadataFetcher
import com.undercover.vpn.shared.parser.happ.HappDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID

object ProfileImporter {

    suspend fun importFromText(input: String): DesktopProfile = withContext(Dispatchers.IO) {
        val trimmed = input.trim()

        // 1. Check if Happ or encrypted V2 link
        if (HappDecoder.isHappOrV2Link(trimmed)) {
            val decrypted = HappDecoder.decrypt(trimmed)
                ?: throw IllegalArgumentException("Failed to decrypt Happ / V2 link")
            return@withContext importFromText(decrypted)
        }

        // 2. Check if HTTP / HTTPS subscription URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return@withContext fetchRemoteSubscription(trimmed)
        }

        // 3. Check if standard proxy URI (vless, hysteria2, vmess, etc.)
        if (ConfigParser.isSupportedUri(trimmed)) {
            val jsonConfig = ConfigParser.parseUriToSingBoxJson(trimmed)
                ?: throw IllegalArgumentException("Could not parse proxy URI")
            val name = ConfigParser.parseUriToName(trimmed) ?: "Imported Proxy"
            val migrated = SingBoxConfigMigrator.migrate(jsonConfig)
            val nodes = extractNodes(migrated)

            return@withContext DesktopProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProfileType.SINGLE_LINK,
                sourceUrl = trimmed,
                rawConfig = jsonConfig,
                migratedConfig = migrated,
                outbounds = nodes,
                selectedOutboundTag = nodes.firstOrNull()?.tag
            )
        }

        // 4. Check if raw JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val migrated = SingBoxConfigMigrator.migrate(trimmed)
            val nodes = extractNodes(migrated)
            val name = extractProfileTitle(migrated) ?: "Custom Configuration"

            return@withContext DesktopProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProfileType.CUSTOM_CONFIG,
                rawConfig = trimmed,
                migratedConfig = migrated,
                outbounds = nodes,
                selectedOutboundTag = nodes.firstOrNull()?.tag
            )
        }

        throw IllegalArgumentException("Unsupported link format or invalid configuration")
    }

    suspend fun updateSubscription(profile: DesktopProfile): DesktopProfile = withContext(Dispatchers.IO) {
        val url = profile.sourceUrl ?: throw IllegalStateException("Profile does not have a subscription URL")
        val updated = fetchRemoteSubscription(url)
        profile.copy(
            rawConfig = updated.rawConfig,
            migratedConfig = updated.migratedConfig,
            outbounds = updated.outbounds,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun fetchRemoteSubscription(urlStr: String): DesktopProfile {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        // Set User-Agent so sing-box compatible backends (Remnawave, 3X-UI) return JSON configuration
        conn.setRequestProperty("User-Agent", "SFA/0.4 (sing-box 1.14.4; linux)")
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("Subscription request failed with HTTP $responseCode")
        }

        val headers = conn.headerFields
        val content = conn.inputStream.bufferedReader().use { it.readText() }.trim()

        // Check if response is base64 encoded list of URIs
        if (!content.startsWith("{") && !content.startsWith("[")) {
            val decoded = tryDecodeBase64(content)
            if (decoded != null && (decoded.contains("://") || decoded.contains("vless"))) {
                return parseUriListSubscription(urlStr, decoded)
            }
        }

        // Response is sing-box JSON
        val migrated = SingBoxConfigMigrator.migrate(content)
        val nodes = extractNodes(migrated)
        val metadata = SubscriptionMetadataFetcher.parseMetadata(headers, urlStr, content)
        val title = metadata.title ?: extractProfileTitle(migrated) ?: extractDomainFromUrl(urlStr)

        return DesktopProfile(
            id = UUID.randomUUID().toString(),
            name = title,
            type = ProfileType.REMOTE_SUBSCRIPTION,
            sourceUrl = urlStr,
            rawConfig = content,
            migratedConfig = migrated,
            outbounds = nodes,
            selectedOutboundTag = nodes.firstOrNull()?.tag
        )
    }

    private fun parseUriListSubscription(urlStr: String, content: String): DesktopProfile {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val outboundsArray = org.json.JSONArray()
        val nodeTags = mutableListOf<String>()
        val proxyNodes = mutableListOf<ProxyNode>()

        lines.forEachIndexed { index, line ->
            val effective = if (HappDecoder.isHappOrV2Link(line)) HappDecoder.decrypt(line) ?: line else line
            if (ConfigParser.isSupportedUri(effective)) {
                val singleJson = ConfigParser.parseUriToSingBoxJson(effective)
                if (singleJson != null) {
                    val root = JSONObject(singleJson)
                    val obs = root.optJSONArray("outbounds")
                    if (obs != null && obs.length() > 0) {
                        val ob = obs.getJSONObject(0)
                        val name = ConfigParser.parseUriToName(effective) ?: "Node ${index + 1}"
                        ob.put("tag", name)
                        outboundsArray.put(ob)
                        nodeTags.add(name)
                        proxyNodes.add(
                            ProxyNode(
                                tag = name,
                                type = ob.optString("type", "proxy"),
                                server = ob.optString("server", ""),
                                port = ob.optInt("server_port", 0)
                            )
                        )
                    }
                }
            }
        }

        val selector = JSONObject().apply {
            put("type", "selector")
            put("tag", "proxy")
            put("outbounds", org.json.JSONArray(nodeTags))
            if (nodeTags.isNotEmpty()) put("default", nodeTags.first())
        }
        outboundsArray.put(0, selector)

        val fullConfig = JSONObject().apply {
            put("outbounds", outboundsArray)
        }.toString()

        val migrated = SingBoxConfigMigrator.migrate(fullConfig)
        val title = extractDomainFromUrl(urlStr)

        return DesktopProfile(
            id = UUID.randomUUID().toString(),
            name = title,
            type = ProfileType.REMOTE_SUBSCRIPTION,
            sourceUrl = urlStr,
            rawConfig = fullConfig,
            migratedConfig = migrated,
            outbounds = proxyNodes,
            selectedOutboundTag = proxyNodes.firstOrNull()?.tag
        )
    }

    private fun extractNodes(migratedJson: String): List<ProxyNode> {
        return try {
            val root = JSONObject(migratedJson)
            val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
            val list = mutableListOf<ProxyNode>()
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val type = ob.optString("type")
                if (type == "selector" || type == "urltest" || type == "direct" || type == "block" || type == "dns") {
                    continue
                }
                val tag = ob.optString("tag")
                val server = ob.optString("server")
                val port = ob.optInt("server_port")
                if (tag.isNotEmpty()) {
                    list.add(ProxyNode(tag = tag, type = type.uppercase(), server = server, port = port))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractProfileTitle(migratedJson: String): String? {
        return try {
            val root = JSONObject(migratedJson)
            val outbounds = root.optJSONArray("outbounds") ?: return null
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                if (ob.optString("type") == "selector") {
                    val tag = ob.optString("tag")
                    if (tag.isNotEmpty()) {
                        return tag.replace("→", "").replace("⚡", "").trim()
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDomainFromUrl(url: String): String {
        return try {
            val host = URL(url).host
            host.removePrefix("www.")
        } catch (_: Exception) {
            "Subscription"
        }
    }

    private fun tryDecodeBase64(str: String): String? {
        return try {
            val clean = str.trim().replace('-', '+').replace('_', '/').let {
                when (it.length % 4) {
                    2 -> "$it=="
                    3 -> "$it="
                    else -> it
                }
            }
            String(Base64.getDecoder().decode(clean), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
