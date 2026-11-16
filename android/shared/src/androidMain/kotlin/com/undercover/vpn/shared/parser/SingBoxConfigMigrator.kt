package com.undercover.vpn.shared.parser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Automatically migrates legacy sing-box configuration formats to modern (1.12+ / 1.14+) schema.
 *
 * Commercial VPN providers frequently export legacy configuration blocks that cause fatal startup errors
 * or rejection in modern sing-box 1.14+ cores. This migrator handles:
 *
 * 1. Root-level `dns.fakeip` object removal and migration to `dns.servers` entry of `type = "fakeip"`
 *    (fixes "legacy dns fakeip options are deprecated" fatal error in sing-box 1.14+).
 * 2. Reordering `dns.servers` so that a `fakeip` server is NEVER at index 0
 *    (fixes "initialize DNS server[0]: default server cannot be fakeip" fatal error).
 * 3. Removal of misplaced `strategy`, `address_strategy`, and `domain_strategy` from `dns.servers[...]` and `dns`
 *    (fixes "dns.servers[X].address_strategy: json: unknown field 'address_strategy'" and "strategy" fatal errors),
 *    promoting strategy to top-level `dns.strategy` if not already set.
 * 4. Removal / conversion of legacy `address_resolver` in `dns.servers` and `outbounds`
 *    (fixes "json: unknown field 'address_resolver'").
 * 5. Removal of misplaced `network` and `protocol` from `dns.servers[...]`
 *    (fixes "dns.servers[X].network: json: unknown field 'network'").
 * 6. Migration of `port` to `server_port` and `host` to `server` in `dns.servers` and `outbounds`.
 * 7. Migration of legacy DNS servers using `address` syntax (`local`, `fakeip`, `https://`, `tls://`, etc.)
 *    to modern typed server objects (`type`, `server`, `server_port`, `path`).
 * 8. Removal of deprecated `independent_cache` and `reverse_mapping` from `dns` and `dns.servers`.
 * 9. Migration of legacy inbound fields (`sniff`, `sniff_override_destination`, `sniff_timeout`, `domain_strategy`, `address_strategy`)
 *    (fixes "initialize inbound[X]: legacy inbound fields are deprecated in sing-box 1.11.0 and removed in sing-box 1.13.0"),
 *    converting them into a `route.rules` action `"sniff"` and top-level DNS strategy.
 * 10. Migration of legacy TUN inbound address fields (`inet4_address`, `inet6_address`, `inet4_route_address`, etc.)
 *     into merged `address`, `route_address`, and `route_exclude_address` arrays
 *     (fixes "initialize inbound[X]: legacy tun address fields are deprecated in sing-box 1.10.0 and removed in sing-box 1.12.0").
 * 11. Removal of deprecated special outbounds `type = "block"` and `type = "dns"`
 *     (fixes "dns outbound is deprecated in sing-box 1.11.0 and removed in sing-box 1.13.0, use rule actions instead"),
 *     converting matching route rules to `action = "reject"` and `action = "hijack-dns"`.
 * 12. Migration of legacy WireGuard outbounds to root `endpoints` list
 *     (fixes "outbounds[X].server: json: unknown field 'server'").
 * 13. Removal of legacy direct outbound destination override fields (`override_address`, `override_port`).
 * 14. Removal of misplaced `domain_strategy` / `strategy` / `address_strategy` from `outbounds` and `route.rules`
 *     (fixes "legacy domain strategy options is deprecated" and "unknown field 'domain_strategy'").
 * 15. Migration of legacy `outbound` DNS rules (e.g. `outbound: "any"`) to `route.default_domain_resolver`.
 * 16. Migration of legacy `geosite` and `geoip` database references in rules to `rule_set` and `ip_is_private`
 *     (fixes "geosite database is deprecated in sing-box 1.8.0 and removed in sing-box 1.12.0").
 * 17. Setting `match_response = true` on DNS rules matching `ip_cidr` or `ip_is_private`.
 * 18. Removal of legacy `type = "field"` from `route.rules` and `dns.rules` (v2ray converter artifacts).
 * 19. Migration of `experimental.clash_api.cache_file` to independent `experimental.cache_file`
 *     and `store_rdrc` to `store_dns`.
 */
object SingBoxConfigMigrator {

    private const val DEFAULT_FAKEIP_INET4 = "198.18.0.0/15"

    private data class MigrationContext(
        val blockTags: MutableSet<String> = mutableSetOf("block"),
        val dnsTags: MutableSet<String> = mutableSetOf("dns", "dns-out"),
        var hadInboundSniff: Boolean = false,
        var sniffTimeout: String? = null,
        var modified: Boolean = false
    )

    fun migrate(jsonString: String): String {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{")) return jsonString

        return try {
            val root = JSONObject(jsonString)
            val ctx = MigrationContext()

            // 1. Inbounds migration (sniffing fields, tun address merging)
            if (migrateInbounds(root, ctx)) {
                ctx.modified = true
            }

            // 2. Outbounds & Endpoints migration (block, dns, wireguard, dial strategies)
            if (migrateOutbounds(root, ctx)) {
                ctx.modified = true
            }

            // 3. DNS migration (fakeip, server address formats, fakeip reordering, dns rules)
            if (migrateDns(root, ctx)) {
                ctx.modified = true
            }

            // 4. Route migration (special outbounds to actions, sniff insertion, geosite/geoip, cleanup)
            if (migrateRoute(root, ctx)) {
                ctx.modified = true
            }

            // 5. Experimental migration (clash_api cache_file, store_rdrc -> store_dns)
            if (migrateExperimental(root)) {
                ctx.modified = true
            }

            if (ctx.modified) root.toString(2) else jsonString
        } catch (_: Exception) {
            jsonString
        }
    }

    private fun promoteDnsStrategy(root: JSONObject, strategy: String) {
        if (strategy.isBlank()) return
        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
        if (!dns.has("strategy")) {
            dns.put("strategy", strategy)
        }
    }

    private fun migrateInbounds(root: JSONObject, ctx: MigrationContext): Boolean {
        val inbounds = root.optJSONArray("inbounds") ?: return false
        var modified = false

        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue

            // 1. Merge TUN address fields
            if (inbound.optString("type") == "tun") {
                if (inbound.has("inet4_address") || inbound.has("inet6_address")) {
                    val addrs = mutableListOf<String>()
                    val existing = inbound.opt("address")
                    if (existing is JSONArray) {
                        for (j in 0 until existing.length()) {
                            addrs.add(existing.optString(j))
                        }
                    } else if (existing is String && existing.isNotEmpty()) {
                        addrs.add(existing)
                    }

                    val inet4 = inbound.optString("inet4_address", "")
                    if (inet4.isNotEmpty()) addrs.add(inet4)
                    val inet6 = inbound.optString("inet6_address", "")
                    if (inet6.isNotEmpty()) addrs.add(inet6)

                    inbound.remove("inet4_address")
                    inbound.remove("inet6_address")
                    inbound.put("address", JSONArray(addrs.distinct()))
                    modified = true
                }

                if (inbound.has("inet4_route_address") || inbound.has("inet6_route_address")) {
                    val routes = mergeJsonArrayFields(inbound, "route_address", "inet4_route_address", "inet6_route_address")
                    inbound.remove("inet4_route_address")
                    inbound.remove("inet6_route_address")
                    inbound.put("route_address", routes)
                    modified = true
                }

                if (inbound.has("inet4_route_exclude_address") || inbound.has("inet6_route_exclude_address")) {
                    val excludes = mergeJsonArrayFields(inbound, "route_exclude_address", "inet4_route_exclude_address", "inet6_route_exclude_address")
                    inbound.remove("inet4_route_exclude_address")
                    inbound.remove("inet6_route_exclude_address")
                    inbound.put("route_exclude_address", excludes)
                    modified = true
                }
            }

            // 2. Legacy sniffing and strategy fields on inbounds
            if (inbound.optBoolean("sniff", false) || inbound.optBoolean("sniff_override_destination", false)) {
                ctx.hadInboundSniff = true
            }
            if (inbound.has("sniff_timeout")) {
                ctx.sniffTimeout = inbound.optString("sniff_timeout")
            }
            if (inbound.has("domain_strategy")) {
                val strat = inbound.optString("domain_strategy", "")
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
            }
            if (inbound.has("address_strategy")) {
                val strat = inbound.optString("address_strategy", "")
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
            }

            if (inbound.has("sniff") || inbound.has("sniff_override_destination") ||
                inbound.has("sniff_timeout") || inbound.has("domain_strategy") ||
                inbound.has("address_strategy") || inbound.has("address_resolver") ||
                inbound.has("sniffing")) {
                inbound.remove("sniff")
                inbound.remove("sniff_override_destination")
                inbound.remove("sniff_timeout")
                inbound.remove("domain_strategy")
                inbound.remove("address_strategy")
                inbound.remove("address_resolver")
                inbound.remove("sniffing")
                modified = true
            }
        }

        return modified
    }

    private fun mergeJsonArrayFields(obj: JSONObject, targetKey: String, field1: String, field2: String): JSONArray {
        val result = mutableListOf<String>()
        val existing = obj.opt(targetKey)
        if (existing is JSONArray) {
            for (j in 0 until existing.length()) result.add(existing.optString(j))
        } else if (existing is String && existing.isNotEmpty()) {
            result.add(existing)
        }

        listOf(field1, field2).forEach { f ->
            val v = obj.opt(f)
            if (v is JSONArray) {
                for (j in 0 until v.length()) result.add(v.optString(j))
            } else if (v is String && v.isNotEmpty()) {
                result.add(v)
            }
        }
        return JSONArray(result.distinct())
    }

    private fun migrateOutbounds(root: JSONObject, ctx: MigrationContext): Boolean {
        val outbounds = root.optJSONArray("outbounds") ?: return false
        var modified = false
        val newOutbounds = JSONArray()

        for (i in 0 until outbounds.length()) {
            val item = outbounds.get(i)
            if (item !is JSONObject) {
                newOutbounds.put(item)
                continue
            }

            val outbound = item
            val type = outbound.optString("type", "")
            val tag = outbound.optString("tag", "")

            // 1. Legacy special outbounds: block & dns
            if (type == "block") {
                if (tag.isNotEmpty()) ctx.blockTags.add(tag)
                modified = true
                continue
            }
            if (type == "dns") {
                if (tag.isNotEmpty()) ctx.dnsTags.add(tag)
                modified = true
                continue
            }

            // 2. Legacy wireguard outbound -> migrate to root endpoints
            if (type == "wireguard" && (outbound.has("server") || outbound.has("peer_public_key"))) {
                val endpoints = root.optJSONArray("endpoints") ?: JSONArray().also { root.put("endpoints", it) }
                val ep = JSONObject().apply {
                    put("type", "wireguard")
                    put("tag", tag.ifEmpty { "wg-ep" })
                    val addr = outbound.opt("local_address") ?: outbound.opt("address")
                    if (addr != null) put("address", addr)
                    if (outbound.has("private_key")) put("private_key", outbound.optString("private_key"))
                    if (outbound.has("mtu")) put("mtu", outbound.optInt("mtu"))

                    val peer = JSONObject().apply {
                        if (outbound.has("server")) put("address", outbound.optString("server"))
                        if (outbound.has("server_port")) put("port", outbound.optInt("server_port", 51820))
                        if (outbound.has("peer_public_key")) put("public_key", outbound.optString("peer_public_key"))
                        if (outbound.has("pre_shared_key")) put("pre_shared_key", outbound.optString("pre_shared_key"))
                        if (outbound.has("reserved")) put("reserved", outbound.opt("reserved"))
                        put("allowed_ips", JSONArray().apply { put("0.0.0.0/0"); put("::/0") })
                    }
                    put("peers", JSONArray().apply { put(peer) })
                }
                endpoints.put(ep)
                modified = true
                // Do not keep in outbounds array; sing-box routes to endpoints by tag seamlessly
                continue
            }

            // 3. Remove direct outbound override fields
            if (type == "direct") {
                if (outbound.has("override_address") || outbound.has("override_port")) {
                    outbound.remove("override_address")
                    outbound.remove("override_port")
                    modified = true
                }
            }

            // 4. Misplaced strategy / domain_strategy / address_strategy
            if (outbound.has("strategy")) {
                val strat = outbound.optString("strategy", "")
                outbound.remove("strategy")
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
                modified = true
            }
            if (outbound.has("domain_strategy")) {
                val strat = outbound.optString("domain_strategy", "")
                outbound.remove("domain_strategy")
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
                modified = true
            }
            if (outbound.has("address_strategy")) {
                val strat = outbound.optString("address_strategy", "")
                outbound.remove("address_strategy")
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
                modified = true
            }

            // 5. Legacy address_resolver in outbound
            if (outbound.has("address_resolver")) {
                val resolver = outbound.optString("address_resolver", "")
                outbound.remove("address_resolver")
                if (resolver.isNotEmpty() && !outbound.has("domain_resolver")) {
                    outbound.put("domain_resolver", resolver)
                }
                modified = true
            }

            // 6. Migrate port -> server_port, host -> server if needed
            if (outbound.has("port") && !outbound.has("server_port")) {
                outbound.put("server_port", outbound.opt("port"))
                outbound.remove("port")
                modified = true
            }
            if (outbound.has("host") && !outbound.has("server")) {
                outbound.put("server", outbound.opt("host"))
                outbound.remove("host")
                modified = true
            }

            newOutbounds.put(outbound)
        }

        root.put("outbounds", newOutbounds)
        return modified
    }

    private fun migrateDns(root: JSONObject, ctx: MigrationContext): Boolean {
        val dns = root.optJSONObject("dns") ?: return false
        var modified = false

        // 1. Remove independent_cache and reverse_mapping
        if (dns.has("independent_cache")) {
            dns.remove("independent_cache")
            modified = true
        }
        if (dns.has("reverse_mapping")) {
            dns.remove("reverse_mapping")
            modified = true
        }

        // 2. Remove address_strategy / address_resolver from dns root if present
        if (dns.has("address_strategy")) {
            val strat = dns.optString("address_strategy", "")
            dns.remove("address_strategy")
            modified = true
            if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
        }
        if (dns.has("address_resolver")) {
            val resolver = dns.optString("address_resolver", "")
            dns.remove("address_resolver")
            modified = true
            if (resolver.isNotEmpty()) {
                val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
                if (!route.has("default_domain_resolver")) {
                    route.put("default_domain_resolver", resolver)
                }
            }
        }

        // 3. Inspect legacy fakeip block
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

            dns.remove("fakeip")
            modified = true
        }

        // 4. Migrate servers array
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

            // Remove misplaced 'address_strategy', 'strategy', and 'domain_strategy'
            if (serverObj.has("address_strategy")) {
                val strat = serverObj.optString("address_strategy", "")
                serverObj.remove("address_strategy")
                modified = true
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
            }
            if (serverObj.has("strategy")) {
                val strat = serverObj.optString("strategy", "")
                serverObj.remove("strategy")
                modified = true
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
            }
            if (serverObj.has("domain_strategy")) {
                val strat = serverObj.optString("domain_strategy", "")
                serverObj.remove("domain_strategy")
                modified = true
                if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
            }
            if (serverObj.has("independent_cache")) {
                serverObj.remove("independent_cache")
                modified = true
            }

            // Remove/convert legacy address_resolver
            if (serverObj.has("address_resolver")) {
                val resolver = serverObj.optString("address_resolver", "")
                serverObj.remove("address_resolver")
                modified = true
                if (resolver.isNotEmpty() && !serverObj.has("domain_resolver")) {
                    serverObj.put("domain_resolver", resolver)
                }
            }

            // Remove misplaced network and protocol from dns server
            if (serverObj.has("network")) {
                val net = serverObj.optString("network", "")
                serverObj.remove("network")
                modified = true
                if (serverObj.optString("type").isEmpty()) {
                    if (net.contains("tcp")) serverObj.put("type", "tcp")
                    else if (net.contains("udp")) serverObj.put("type", "udp")
                }
            }
            if (serverObj.has("protocol")) {
                serverObj.remove("protocol")
                modified = true
            }

            // Migrate port -> server_port, host -> server
            if (serverObj.has("port")) {
                val p = serverObj.optInt("port", 0)
                serverObj.remove("port")
                if (p > 0 && !serverObj.has("server_port")) {
                    serverObj.put("server_port", p)
                }
                modified = true
            }
            if (serverObj.has("host")) {
                val h = serverObj.optString("host", "")
                serverObj.remove("host")
                if (h.isNotEmpty() && !serverObj.has("server")) {
                    serverObj.put("server", h)
                }
                modified = true
            }

            val type = serverObj.optString("type", "")
            val address = serverObj.optString("address", "")
            val tag = serverObj.optString("tag", "")

            val isFakeIp = type == "fakeip" || address == "fakeip" ||
                    tag == "fakeip" || tag == "fakeip-dns" || tag == "dns-fakeip"

            if (isFakeIp) {
                if (!fakeIpEnabled) {
                    modified = true
                    continue
                }
                foundFakeIpServer = true
                serverObj.put("type", "fakeip")
                serverObj.remove("address")
                serverObj.remove("server")
                serverObj.remove("server_port")
                serverObj.remove("detour")
                serverObj.remove("domain_resolver")
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

            if (serverObj.optString("type") == "local" || address == "local") {
                serverObj.remove("server")
                serverObj.remove("server_port")
                serverObj.remove("domain_resolver")
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

        // 5. CRITICAL: sing-box 1.12+ requirement:
        // "initialize DNS server[0]: default server cannot be fakeip"
        // Ensure server at index 0 is NEVER fakeip!
        val orderedServers = JSONArray()
        val fakeIpServers = mutableListOf<JSONObject>()
        val realServers = mutableListOf<JSONObject>()

        for (i in 0 until newServers.length()) {
            val s = newServers.optJSONObject(i) ?: continue
            if (s.optString("type") == "fakeip") {
                fakeIpServers.add(s)
            } else {
                realServers.add(s)
            }
        }

        if (realServers.isEmpty() && fakeIpServers.isNotEmpty()) {
            // No real DNS server present; insert local fallback first
            val localFallback = JSONObject().apply {
                put("tag", "dns-local")
                put("type", "local")
            }
            orderedServers.put(localFallback)
            modified = true
        } else {
            realServers.forEach { orderedServers.put(it) }
        }
        fakeIpServers.forEach { orderedServers.put(it) }

        dns.put("servers", orderedServers)

        // 6. Migrate dns.rules
        val dnsRules = dns.optJSONArray("rules")
        if (dnsRules != null) {
            val newDnsRules = JSONArray()
            for (i in 0 until dnsRules.length()) {
                val rule = dnsRules.optJSONObject(i) ?: continue
                rule.remove("type") // Remove "type": "field"
                rule.remove("address_strategy")
                rule.remove("address_resolver")

                // Handle legacy outbound match in dns.rules
                if (rule.has("outbound")) {
                    val ob = rule.opt("outbound")
                    val isAny = ob == "any" || (ob is JSONArray && jsonArrayContains(ob, "any"))
                    val server = rule.optString("server", "")

                    if (isAny && server.isNotEmpty()) {
                        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
                        if (!route.has("default_domain_resolver")) {
                            route.put("default_domain_resolver", server)
                            modified = true
                        }
                    }

                    rule.remove("outbound")
                    modified = true

                    // If the rule was ONLY matching outbound and setting server, remove it entirely
                    if (rule.length() == 1 && rule.has("server")) {
                        continue
                    }
                }

                // Handle legacy geosite / geoip in dns rules
                if (rule.has("geoip")) {
                    val g = rule.opt("geoip")
                    if (g == "private" || (g is JSONArray && g.length() == 1 && g.optString(0) == "private")) {
                        rule.put("ip_is_private", true)
                    } else {
                        rule.put("rule_set", convertGeoToRuleSet("geoip", g))
                    }
                    rule.remove("geoip")
                    modified = true
                }
                if (rule.has("geosite")) {
                    rule.put("rule_set", convertGeoToRuleSet("geosite", rule.opt("geosite")))
                    rule.remove("geosite")
                    modified = true
                }

                // If rule matches IP response fields, ensure match_response = true
                if ((rule.has("ip_cidr") || rule.has("ip_is_private")) && !rule.has("match_response") && !rule.has("action")) {
                    rule.put("match_response", true)
                    modified = true
                }

                newDnsRules.put(rule)
            }
            dns.put("rules", newDnsRules)
        }

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

    private fun migrateRoute(root: JSONObject, ctx: MigrationContext): Boolean {
        val route = root.optJSONObject("route") ?: run {
            if (ctx.hadInboundSniff) {
                JSONObject().also { root.put("route", it) }
            } else {
                return false
            }
        }
        var modified = false
        var rules = route.optJSONArray("rules")
        if (rules == null) {
            rules = JSONArray()
            route.put("rules", rules)
            modified = true
        }

        // Insert action: sniff at top if inbounds had sniffing enabled
        if (ctx.hadInboundSniff) {
            var hasSniffAction = false
            for (i in 0 until rules.length()) {
                val r = rules.optJSONObject(i) ?: continue
                if (r.optString("action") == "sniff") {
                    hasSniffAction = true
                    break
                }
            }
            if (!hasSniffAction) {
                val sniffRule = JSONObject().apply {
                    put("action", "sniff")
                    if (!ctx.sniffTimeout.isNullOrEmpty()) {
                        put("timeout", ctx.sniffTimeout)
                    }
                }
                val newRulesWithSniff = JSONArray()
                newRulesWithSniff.put(sniffRule)
                for (i in 0 until rules.length()) {
                    newRulesWithSniff.put(rules.get(i))
                }
                rules = newRulesWithSniff
                route.put("rules", rules)
                modified = true
            }
        }

        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue

            // 1. Remove v2ray "type": "field" artifact
            if (rule.has("type") && rule.optString("type") == "field") {
                rule.remove("type")
                modified = true
            }

            // 2. Rewrite special outbounds (block -> reject, dns -> hijack-dns)
            if (rule.has("outbound")) {
                val ob = rule.optString("outbound")
                if (ctx.blockTags.contains(ob)) {
                    rule.remove("outbound")
                    rule.put("action", "reject")
                    modified = true
                } else if (ctx.dnsTags.contains(ob)) {
                    rule.remove("outbound")
                    rule.put("action", "hijack-dns")
                    modified = true
                }
            }

            // 3. Remove misplaced strategy / domain_strategy / address_strategy unless rule action is resolve
            if (rule.optString("action") != "resolve") {
                if (rule.has("strategy")) {
                    val strat = rule.optString("strategy", "")
                    rule.remove("strategy")
                    if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
                    modified = true
                }
                if (rule.has("domain_strategy")) {
                    val strat = rule.optString("domain_strategy", "")
                    rule.remove("domain_strategy")
                    if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
                    modified = true
                }
                if (rule.has("address_strategy")) {
                    val strat = rule.optString("address_strategy", "")
                    rule.remove("address_strategy")
                    if (strat.isNotEmpty()) promoteDnsStrategy(root, strat)
                    modified = true
                }
            }

            // 4. Remove address_resolver
            if (rule.has("address_resolver")) {
                rule.remove("address_resolver")
                modified = true
            }

            // 5. Migrate legacy geosite / geoip in route rules
            if (rule.has("geoip")) {
                val g = rule.opt("geoip")
                if (g == "private" || (g is JSONArray && g.length() == 1 && g.optString(0) == "private")) {
                    rule.put("ip_is_private", true)
                } else {
                    rule.put("rule_set", convertGeoToRuleSet("geoip", g))
                }
                rule.remove("geoip")
                modified = true
            }
            if (rule.has("geosite")) {
                rule.put("rule_set", convertGeoToRuleSet("geosite", rule.opt("geosite")))
                rule.remove("geosite")
                modified = true
            }
            if (rule.has("source_geoip")) {
                rule.put("rule_set", convertGeoToRuleSet("geoip", rule.opt("source_geoip")))
                rule.put("rule_set_ipcidr_match_source", true)
                rule.remove("source_geoip")
                modified = true
            }
        }

        return modified
    }

    private fun convertGeoToRuleSet(prefix: String, value: Any?): Any {
        return when (value) {
            is String -> "$prefix-$value"
            is JSONArray -> {
                val arr = JSONArray()
                for (i in 0 until value.length()) {
                    val item = value.optString(i)
                    if (item.isNotEmpty()) arr.put("$prefix-$item")
                }
                arr
            }
            else -> "$prefix-$value"
        }
    }

    private fun jsonArrayContains(arr: JSONArray, value: String): Boolean {
        for (i in 0 until arr.length()) {
            if (arr.optString(i) == value) return true
        }
        return false
    }

    private fun migrateExperimental(root: JSONObject): Boolean {
        val experimental = root.optJSONObject("experimental") ?: return false
        var modified = false

        // 1. Migrate clash_api.cache_file
        val clashApi = experimental.optJSONObject("clash_api")
        if (clashApi != null && clashApi.has("cache_file")) {
            val cacheFilePath = clashApi.optString("cache_file", "")
            val cacheId = clashApi.optString("cache_id", "")
            val storeFakeIp = clashApi.optBoolean("store_fakeip", true)

            clashApi.remove("cache_file")
            clashApi.remove("cache_id")
            clashApi.remove("store_fakeip")
            clashApi.remove("store_mode")
            clashApi.remove("store_selected")

            val cacheFile = experimental.optJSONObject("cache_file") ?: JSONObject().also {
                experimental.put("cache_file", it)
            }
            cacheFile.put("enabled", true)
            if (cacheFilePath.isNotEmpty() && !cacheFile.has("path")) {
                cacheFile.put("path", cacheFilePath)
            }
            if (cacheId.isNotEmpty() && !cacheFile.has("cache_id")) {
                cacheFile.put("cache_id", cacheId)
            }
            if (!cacheFile.has("store_fakeip")) {
                cacheFile.put("store_fakeip", storeFakeIp)
            }
            modified = true
        }

        // 2. Migrate store_rdrc -> store_dns
        val cacheFile = experimental.optJSONObject("cache_file")
        if (cacheFile != null && cacheFile.has("store_rdrc")) {
            val storeRdrc = cacheFile.optBoolean("store_rdrc", false)
            cacheFile.remove("store_rdrc")
            if (!cacheFile.has("store_dns")) {
                cacheFile.put("store_dns", storeRdrc)
            }
            modified = true
        }

        return modified
    }
}
