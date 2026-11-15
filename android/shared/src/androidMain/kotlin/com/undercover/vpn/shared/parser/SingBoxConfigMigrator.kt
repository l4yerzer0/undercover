package com.undercover.vpn.shared.parser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Automatically migrates legacy sing-box configuration formats to modern (1.12+ / 1.14+) schema.
 *
 * Specifically handles:
 * 1. Removal of root-level `dns.fakeip` object and migration to `dns.servers` entry of `type = "fakeip"`
 *    (fixes "legacy dns fakeip options are deprecated" fatal error in sing-box 1.14+).
 * 2. Migration of legacy DNS servers using `address` syntax (e.g. `address: "local"`, `address: "fakeip"`,
 *    `address: "https://..."`, etc.) to explicit `type` + `server` objects.
 * 3. Removal of deprecated `independent_cache` under `dns`.
 * 4. Migration of deprecated `store_rdrc` to `store_dns` in `experimental.cache_file`.
 */
object SingBoxConfigMigrator {

    private const val DEFAULT_FAKEIP_INET4 = "198.18.0.0/15"

    fun migrate(jsonString: String): String {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{")) return jsonString

        return try {
            val root = JSONObject(jsonString)
            var modified = false

            if (migrateDns(root)) {
                modified = true
            }
            if (migrateExperimental(root)) {
                modified = true
            }

            if (modified) root.toString(2) else jsonString
        } catch (_: Exception) {
            jsonString
        }
    }

    private fun migrateDns(root: JSONObject): Boolean {
        val dns = root.optJSONObject("dns") ?: return false
        var modified = false

        // 1. Remove independent_cache if present
        if (dns.has("independent_cache")) {
            dns.remove("independent_cache")
            modified = true
        }

        // 2. Inspect legacy fakeip block
        val legacyFakeIp = dns.optJSONObject("fakeip")
        var fakeIpEnabled = true
        var fakeIpInet4Range: String? = null
        var fakeIpInet6Range: String? = null

        if (legacyFakeIp != null) {
            fakeIpEnabled = legacyFakeIp.optBoolean("enabled", true)
            fakeIpInet4Range = legacyFakeIp.optString("inet4_range", "")
                .ifEmpty { legacyFakeIp.optString("range", "") }
                .takeIf { it.isNotEmpty() }
            fakeIpInet6Range = legacyFakeIp.optString("inet6_range", "")
                .takeIf { it.isNotEmpty() }

            // MUST remove "fakeip" from dns to avoid "legacy dns fakeip options are deprecated"
            dns.remove("fakeip")
            modified = true
        }

        // 3. Migrate servers array
        var servers = dns.optJSONArray("servers")
        if (servers == null) {
            servers = JSONArray()
            dns.put("servers", servers)
            modified = true
        }

        var foundFakeIpServer = false
        val newServers = JSONArray()

        for (i in 0 until servers.length()) {
            val item = servers.get(i)
            if (item is String) {
                modified = true
                val obj = JSONObject()
                when (item) {
                    "local" -> {
                        obj.put("tag", "local")
                        obj.put("type", "local")
                        newServers.put(obj)
                    }
                    "fakeip" -> {
                        if (fakeIpEnabled) {
                            foundFakeIpServer = true
                            obj.put("tag", "fakeip")
                            obj.put("type", "fakeip")
                            obj.put("inet4_range", fakeIpInet4Range ?: DEFAULT_FAKEIP_INET4)
                            if (fakeIpInet6Range != null) obj.put("inet6_range", fakeIpInet6Range)
                            newServers.put(obj)
                        }
                    }
                    else -> {
                        obj.put("tag", "dns-$i")
                        migrateLegacyDnsAddress(obj, item)
                        newServers.put(obj)
                    }
                }
                continue
            }

            if (item !is JSONObject) {
                newServers.put(item)
                continue
            }

            val serverObj = item
            val type = serverObj.optString("type", "")
            val address = serverObj.optString("address", "")
            val tag = serverObj.optString("tag", "")

            val isFakeIp = type == "fakeip" || address == "fakeip" ||
                    tag == "fakeip" || tag == "fakeip-dns" || tag == "dns-fakeip"

            if (isFakeIp) {
                if (!fakeIpEnabled) {
                    // FakeIP was explicitly disabled; omit this server
                    modified = true
                    continue
                }
                foundFakeIpServer = true
                serverObj.put("type", "fakeip")
                if (serverObj.has("address")) {
                    serverObj.remove("address")
                    modified = true
                }
                if (!serverObj.has("inet4_range")) {
                    serverObj.put("inet4_range", fakeIpInet4Range ?: DEFAULT_FAKEIP_INET4)
                    modified = true
                }
                if (fakeIpInet6Range != null && !serverObj.has("inet6_range")) {
                    serverObj.put("inet6_range", fakeIpInet6Range)
                    modified = true
                }
                newServers.put(serverObj)
                continue
            }

            if (address.isNotEmpty()) {
                migrateLegacyDnsAddress(serverObj, address)
                modified = true
            }
            newServers.put(serverObj)
        }

        // If fakeip was configured but not present in servers array, add it
        if (legacyFakeIp != null && fakeIpEnabled && !foundFakeIpServer) {
            val fakeIpServer = JSONObject().apply {
                put("tag", "fakeip")
                put("type", "fakeip")
                put("inet4_range", fakeIpInet4Range ?: DEFAULT_FAKEIP_INET4)
                if (fakeIpInet6Range != null) {
                    put("inet6_range", fakeIpInet6Range)
                }
            }
            newServers.put(fakeIpServer)
            modified = true
        }

        dns.put("servers", newServers)
        return modified
    }

    private fun migrateLegacyDnsAddress(serverObj: JSONObject, address: String) {
        serverObj.remove("address")

        when {
            address == "local" -> {
                serverObj.put("type", "local")
            }
            address.startsWith("dhcp://") -> {
                serverObj.put("type", "dhcp")
            }
            address.startsWith("https://") || address.startsWith("http://") -> {
                serverObj.put("type", "https")
                try {
                    val uri = URI(address)
                    serverObj.put("server", uri.host ?: address.removePrefix("https://").removePrefix("http://").substringBefore("/"))
                    if (uri.port > 0) serverObj.put("server_port", uri.port)
                    val path = uri.rawPath
                    if (!path.isNullOrEmpty() && path != "/") {
                        serverObj.put("path", path)
                    }
                } catch (_: Exception) {
                    serverObj.put("server", address.removePrefix("https://").removePrefix("http://").substringBefore("/"))
                }
            }
            address.startsWith("tls://") -> {
                serverObj.put("type", "tls")
                val clean = address.removePrefix("tls://")
                val host = clean.substringBefore(":")
                val port = clean.substringAfter(":", "").toIntOrNull()
                serverObj.put("server", host)
                if (port != null && port > 0) serverObj.put("server_port", port)
            }
            address.startsWith("tcp://") -> {
                serverObj.put("type", "tcp")
                val clean = address.removePrefix("tcp://")
                val host = clean.substringBefore(":")
                val port = clean.substringAfter(":", "").toIntOrNull()
                serverObj.put("server", host)
                if (port != null && port > 0) serverObj.put("server_port", port)
            }
            address.startsWith("udp://") -> {
                serverObj.put("type", "udp")
                val clean = address.removePrefix("udp://")
                val host = clean.substringBefore(":")
                val port = clean.substringAfter(":", "").toIntOrNull()
                serverObj.put("server", host)
                if (port != null && port > 0) serverObj.put("server_port", port)
            }
            address.startsWith("quic://") -> {
                serverObj.put("type", "quic")
                val clean = address.removePrefix("quic://")
                val host = clean.substringBefore(":")
                val port = clean.substringAfter(":", "").toIntOrNull()
                serverObj.put("server", host)
                if (port != null && port > 0) serverObj.put("server_port", port)
            }
            address.startsWith("h3://") -> {
                serverObj.put("type", "h3")
                val clean = address.removePrefix("h3://")
                val host = clean.substringBefore(":")
                val port = clean.substringAfter(":", "").toIntOrNull()
                serverObj.put("server", host)
                if (port != null && port > 0) serverObj.put("server_port", port)
            }
            else -> {
                // Raw IP or host
                if (!serverObj.has("type")) {
                    serverObj.put("type", "udp")
                    val host = address.substringBefore(":")
                    val port = address.substringAfter(":", "").toIntOrNull()
                    serverObj.put("server", host)
                    if (port != null && port > 0) serverObj.put("server_port", port)
                }
            }
        }
    }

    private fun migrateExperimental(root: JSONObject): Boolean {
        val experimental = root.optJSONObject("experimental") ?: return false
        val cacheFile = experimental.optJSONObject("cache_file") ?: return false
        if (cacheFile.has("store_rdrc")) {
            val storeRdrc = cacheFile.optBoolean("store_rdrc", false)
            cacheFile.remove("store_rdrc")
            if (!cacheFile.has("store_dns")) {
                cacheFile.put("store_dns", storeRdrc)
            }
            return true
        }
        return false
    }
}
