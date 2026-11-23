package com.undercover.vpn.routing

import android.util.Log
import com.undercover.vpn.bg.XrayManager
import com.undercover.vpn.database.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom

/**
 * Generates per-app and per-domain routing rules from the UI settings and injects them
 * into the profile on its way to the core.
 *
 * The profile on disk is never touched directly: rules are added to an in-memory copy at
 * every start and reload, so the user's JSON stays theirs and nothing
 * accumulates across restarts. That also lets the ByeDPI outbound carry a port
 * that is only known at runtime.
 */
object RoutingConfig {
    private const val TAG = "RoutingConfig"
    private const val BYEDPI_ADDRESS = "127.0.0.1"
    private const val PROBE_INBOUND_TAG = "undercover-probe-in"
    private const val DIRECT_DNS_SERVER_TAG = "undercover-dns-local"
    private val secureRandom = SecureRandom()

    data class ProbeEndpoint(
        val port: Int,
        val username: String,
        val password: String,
    )

    /** Ephemeral authenticated loopback mixed inbound used by IpInfoProbe & LatencyProbe. */
    @Volatile
    var activeProbeEndpoint: ProbeEndpoint? = null
        private set

    fun clearProbeEndpoint() {
        activeProbeEndpoint = null
    }

    private fun allocateProbeEndpoint(): ProbeEndpoint {
        val port = ServerSocket(0, 1, InetAddress.getByName(BYEDPI_ADDRESS)).use { it.localPort }
        return ProbeEndpoint(
            port = port,
            username = randomHex(12),
            password = randomHex(24),
        )
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        val sb = StringBuilder(byteCount * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    val RU_DOMAINS_PRESET = listOf(
        "ru",
        "su",
        "xn--p1ai", // .рф
        "by",
        "kz",
        "yandex.ru",
        "yandex.net",
        "ya.ru",
        "vk.com",
        "vkuser.net",
        "vk-cdn.net",
        "vkvideo.ru",
        "mycdn.me",
        "userapi.com",
        "ok.ru",
        "mail.ru",
        "imgsmail.ru",
        "gosuslugi.ru",
        "mos.ru",
        "sberbank.ru",
        "sber.ru",
        "tbank.ru",
        "tinkoff.ru",
        "alfabank.ru",
        "vtb.ru",
        "ozon.ru",
        "ozonusercontent.com",
        "wildberries.ru",
        "wb.ru",
        "wbstatic.net",
        "avito.ru",
        "avito.st",
        "kinopoisk.ru",
        "rutube.ru",
        "dzen.ru",
        "2gis.ru",
        "2gis.com",
        "hh.ru",
        "cian.ru",
        "auto.ru",
        "pochta.ru",
        "nalog.gov.ru",
        "pfr.gov.ru",
        "rambler.ru",
        "lenta.ru",
        "rbc.ru",
        "ria.ru",
        "kp.ru",
        "kommersant.ru",
        "kinopoisk-ru.clstorage.net",
        "yastatic.net",
    )

    val YOUTUBE_DOMAINS_PRESET = listOf(
        "youtube.com",
        "googlevideo.com",
        "ytimg.com",
        "youtu.be",
        "ggpht.com",
        "yt.be",
        "youtubeeducation.com",
        "youtubekids.com",
    )

    val DISCORD_DOMAINS_PRESET = listOf(
        "discord.com",
        "discordapp.com",
        "discord.gg",
        "discord.media",
        "discordapp.net",
        "discordstatus.com",
        "dis.gd",
    )

    /**
     * Returns [configJson] with generated outbounds, authenticated loopback endpoints,
     * Android TUN/DNS repairs, and route rules.
     */
    fun apply(
        configJson: String,
        byeDpiPort: Int?,
        xrayBridgeTags: Set<String> = emptySet(),
        xrayBridge: XrayManager.BridgeEndpoint? = null,
    ): String {
        val directApps = Settings.routingList(RoutingSection.DIRECT)
        val byeDpiApps = if (byeDpiPort != null) {
            Settings.routingList(RoutingSection.BYEDPI)
        } else {
            emptySet()
        }

        val directDomains = mutableListOf<String>()
        if (Settings.routingDirectRuSites) {
            directDomains.addAll(RU_DOMAINS_PRESET)
        }
        directDomains.addAll(Settings.routingCustomDirectDomains)

        val byeDpiDomains = mutableListOf<String>()
        if (byeDpiPort != null) {
            if (Settings.routingByeDpiYouTube) {
                byeDpiDomains.addAll(YOUTUBE_DOMAINS_PRESET)
            }
            if (Settings.routingByeDpiDiscord) {
                byeDpiDomains.addAll(DISCORD_DOMAINS_PRESET)
            }
            byeDpiDomains.addAll(Settings.routingCustomByeDpiDomains)
        }

        return try {
            val config = JSONObject(configJson)
            stripGenerated(config)
            repairAndroidConfig(config)
            ensureProbeInbound(config)
            if (xrayBridge != null && xrayBridgeTags.isNotEmpty()) {
                applyXrayBridgeCredentials(config, xrayBridgeTags, xrayBridge)
            }
            val distinctDirectDomains = directDomains.distinct()
            val distinctByeDpiDomains = byeDpiDomains.distinct()
            if (directApps.isNotEmpty() || distinctDirectDomains.isNotEmpty()) ensureDirectOutbound(config)
            if ((byeDpiApps.isNotEmpty() || distinctByeDpiDomains.isNotEmpty()) && byeDpiPort != null) {
                ensureByeDpiOutbound(config, byeDpiPort)
            }
            insertRules(config, directApps, byeDpiApps, distinctDirectDomains, distinctByeDpiDomains)
            if (distinctDirectDomains.isNotEmpty()) {
                insertDirectDnsRule(config, distinctDirectDomains)
            }
            Log.i(
                TAG,
                "routing applied: directApps=${directApps.size} directDomains=${distinctDirectDomains.size} " +
                    "byedpiApps=${byeDpiApps.size} byedpiDomains=${distinctByeDpiDomains.size} " +
                    "xrayBridges=${xrayBridgeTags.size}",
            )
            config.toString()
        } catch (e: Exception) {
            Log.e(TAG, "failed to inject routing rules, running profile unmodified", e)
            configJson
        }
    }

    /**
     * Removes rules, inbounds, DNS entries, and outbounds generated on an earlier run.
     */
    private fun stripGenerated(config: JSONObject) {
        val tags = RoutingSection.entries.map { it.outboundTag }.toSet()

        config.optJSONObject("route")?.optJSONArray("rules")?.let { rules ->
            removeMatching(rules) { rule ->
                val hasTarget = rule.has("package_name") || rule.has("domain_suffix")
                if (!hasTarget) return@removeMatching false
                val outbound = rule.optString("outbound")
                val isOurRoute = outbound in tags
                val isOurReject = rule.optString("action") == "reject" &&
                    rule.optString("network") == "udp" &&
                    rule.optInt("port") == 443
                isOurRoute || isOurReject
            }
        }

        config.optJSONArray("outbounds")?.let { outbounds ->
            removeMatching(outbounds) { it.optString("tag") in tags }
        }

        config.optJSONArray("inbounds")?.let { inbounds ->
            removeMatching(inbounds) { it.optString("tag") == PROBE_INBOUND_TAG }
        }

        config.optJSONObject("dns")?.let { dns ->
            dns.optJSONArray("rules")?.let { dnsRules ->
                removeMatching(dnsRules) { it.optString("server") == DIRECT_DNS_SERVER_TAG }
            }
            dns.optJSONArray("servers")?.let { dnsServers ->
                removeMatching(dnsServers) { it.optString("tag") == DIRECT_DNS_SERVER_TAG }
            }
        }
    }

    /**
     * Repairs previously-generated profiles on Android:
     *  1. Replaces TUN `stack: "system"` with `stack: "gvisor"` (non-root Android VpnService drops TCP on `system`).
     *  2. Ensures TUN `address` includes IPv6 (`fdfe:dcba:9876::1/126`) so dual-stack IPv6 traffic cannot leak.
     *  3. Upgrades the old single direct `cloudflare-doh` (blocked in RU, no detour) to
     *     `dns-remote` (DoH via proxy) + `dns-local` (Android system DNS as default_domain_resolver).
     */
    private fun repairAndroidConfig(config: JSONObject) {
        config.optJSONArray("inbounds")?.let { inbounds ->
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                if (inbound.optString("type") == "tun") {
                    if (inbound.optString("stack") == "system") {
                        inbound.put("stack", "gvisor")
                    }
                    val addrArr = inbound.optJSONArray("address")
                    if (addrArr != null) {
                        var hasIpv6 = false
                        for (j in 0 until addrArr.length()) {
                            if (addrArr.optString(j).contains(':')) {
                                hasIpv6 = true
                                break
                            }
                        }
                        if (!hasIpv6) {
                            addrArr.put("fdfe:dcba:9876::1/126")
                        }
                    }
                }
            }
        }

        val dns = config.optJSONObject("dns")
        val servers = dns?.optJSONArray("servers")
        if (dns != null && servers != null && servers.length() == 1) {
            val only = servers.optJSONObject(0)
            if (only != null &&
                only.optString("tag") == "cloudflare-doh" &&
                only.optString("detour").isEmpty()
            ) {
                val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
                val detourTag = route.optString("final").ifEmpty { "proxy" }
                val remoteDns = JSONObject()
                    .put("tag", "dns-remote")
                    .put("type", "https")
                    .put("server", "1.1.1.1")
                    .put("detour", detourTag)
                val localDns = JSONObject()
                    .put("tag", "dns-local")
                    .put("type", "local")
                dns.put("servers", JSONArray().put(remoteDns).put(localDns))
                dns.put("final", "dns-remote")
                dns.put("strategy", "prefer_ipv4")
                route.put("default_domain_resolver", "dns-local")
            }
        }
    }

    private fun ensureProbeInbound(config: JSONObject) {
        val endpoint = allocateProbeEndpoint()
        activeProbeEndpoint = endpoint
        val inbounds = config.optJSONArray("inbounds") ?: JSONArray().also { config.put("inbounds", it) }
        inbounds.put(
            JSONObject()
                .put("type", "mixed")
                .put("tag", PROBE_INBOUND_TAG)
                .put("listen", BYEDPI_ADDRESS)
                .put("listen_port", endpoint.port)
                .put(
                    "users",
                    JSONArray().put(
                        JSONObject()
                            .put("username", endpoint.username)
                            .put("password", endpoint.password),
                    ),
                ),
        )
    }

    private fun applyXrayBridgeCredentials(
        config: JSONObject,
        xrayBridgeTags: Set<String>,
        bridge: XrayManager.BridgeEndpoint,
    ) {
        val outbounds = config.optJSONArray("outbounds") ?: return
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.optJSONObject(i) ?: continue
            if (ob.optString("type").equals("socks", ignoreCase = true) &&
                ob.optString("tag") in xrayBridgeTags
            ) {
                ob.put("server", BYEDPI_ADDRESS)
                ob.put("server_port", bridge.port)
                ob.put("version", "5")
                ob.put("username", bridge.username)
                ob.put("password", bridge.password)
            }
        }
    }

    private fun insertDirectDnsRule(config: JSONObject, directDomains: List<String>) {
        val dns = config.optJSONObject("dns") ?: return
        val servers = dns.optJSONArray("servers") ?: return
        if (servers.length() == 0) return

        servers.put(
            JSONObject()
                .put("tag", DIRECT_DNS_SERVER_TAG)
                .put("type", "local"),
        )
        val rules = dns.optJSONArray("rules") ?: JSONArray().also { dns.put("rules", it) }
        insertAt(
            rules,
            0,
            JSONObject()
                .put("domain_suffix", JSONArray(directDomains))
                .put("server", DIRECT_DNS_SERVER_TAG),
        )
    }

    private fun removeMatching(array: JSONArray, predicate: (JSONObject) -> Boolean) {
        for (index in array.length() - 1 downTo 0) {
            val item = array.optJSONObject(index) ?: continue
            if (predicate(item)) array.remove(index)
        }
    }

    private fun ensureDirectOutbound(config: JSONObject) {
        val outbounds = outbounds(config)
        if (findByTag(outbounds, RoutingSection.DIRECT.outboundTag) != null) return
        outbounds.put(
            JSONObject()
                .put("type", "direct")
                .put("tag", RoutingSection.DIRECT.outboundTag),
        )
    }

    private fun ensureByeDpiOutbound(config: JSONObject, port: Int) {
        val outbounds = outbounds(config)
        val outbound = findByTag(outbounds, RoutingSection.BYEDPI.outboundTag)
            ?: JSONObject().also { outbounds.put(it) }
        outbound.put("type", "socks")
        outbound.put("tag", RoutingSection.BYEDPI.outboundTag)
        outbound.put("server", BYEDPI_ADDRESS)
        outbound.put("server_port", port)
        outbound.put("version", "5")
    }

    private fun insertRules(
        config: JSONObject,
        directApps: Set<String>,
        byeDpiApps: Set<String>,
        directDomains: List<String>,
        byeDpiDomains: List<String>,
    ) {
        val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }

        val generated = mutableListOf<JSONObject>()

        // 1. Direct apps FIRST so apps marked "direct" are strictly isolated from VPN/ByeDPI
        if (directApps.isNotEmpty()) {
            generated += JSONObject()
                .put("package_name", JSONArray(directApps.toList()))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        // 2. ByeDPI rules (domains & apps)
        if (byeDpiDomains.isNotEmpty()) {
            generated += JSONObject()
                .put("domain_suffix", JSONArray(byeDpiDomains))
                .put("network", "udp")
                .put("port", 443)
                .put("action", "reject")
            generated += JSONObject()
                .put("domain_suffix", JSONArray(byeDpiDomains))
                .put("action", "route")
                .put("outbound", RoutingSection.BYEDPI.outboundTag)
        }

        if (byeDpiApps.isNotEmpty()) {
            generated += JSONObject()
                .put("package_name", JSONArray(byeDpiApps.toList()))
                .put("network", "udp")
                .put("port", 443)
                .put("action", "reject")
            generated += JSONObject()
                .put("package_name", JSONArray(byeDpiApps.toList()))
                .put("action", "route")
                .put("outbound", RoutingSection.BYEDPI.outboundTag)
        }

        // 3. Direct domains
        if (directDomains.isNotEmpty()) {
            generated += JSONObject()
                .put("domain_suffix", JSONArray(directDomains))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        val insertAt = leadingPreambleLength(rules)
        generated.forEachIndexed { offset, rule ->
            insertAt(rules, insertAt + offset, rule)
        }
    }

    /** Number of leading rules that only sniff or hijack DNS. */
    private fun leadingPreambleLength(rules: JSONArray): Int {
        var index = 0
        while (index < rules.length()) {
            val action = rules.optJSONObject(index)?.optString("action")
            if (action != "sniff" && action != "hijack-dns") break
            index++
        }
        return index
    }

    private fun insertAt(array: JSONArray, index: Int, value: JSONObject) {
        val tail = (index until array.length()).map { array.get(it) }
        while (array.length() > index) {
            array.remove(array.length() - 1)
        }
        array.put(value)
        tail.forEach { array.put(it) }
    }

    private fun outbounds(config: JSONObject): JSONArray =
        config.optJSONArray("outbounds") ?: JSONArray().also { config.put("outbounds", it) }

    private fun findByTag(array: JSONArray, tag: String): JSONObject? =
        (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .firstOrNull { it.optString("tag") == tag }
}
