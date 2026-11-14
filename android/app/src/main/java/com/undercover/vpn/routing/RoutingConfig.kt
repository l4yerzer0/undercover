package com.undercover.vpn.routing

import android.util.Log
import com.undercover.vpn.database.Settings
import org.json.JSONArray
import org.json.JSONObject

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
     * Returns [configJson] with generated outbounds and route rules.
     */
    fun apply(configJson: String, byeDpiPort: Int?): String {
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
            if (directApps.isNotEmpty() || directDomains.isNotEmpty()) ensureDirectOutbound(config)
            if ((byeDpiApps.isNotEmpty() || byeDpiDomains.isNotEmpty()) && byeDpiPort != null) {
                ensureByeDpiOutbound(config, byeDpiPort)
            }
            insertRules(config, directApps, byeDpiApps, directDomains.distinct(), byeDpiDomains.distinct())
            Log.i(
                TAG,
                "routing applied: directApps=${directApps.size} directDomains=${directDomains.size} " +
                    "byedpiApps=${byeDpiApps.size} byedpiDomains=${byeDpiDomains.size}",
            )
            config.toString()
        } catch (e: Exception) {
            Log.e(TAG, "failed to inject routing rules, running profile unmodified", e)
            configJson
        }
    }

    /**
     * Removes rules and outbounds generated on an earlier run.
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

        // 1. ByeDPI rules (domains & apps)
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

        // 2. Direct rules (domains & apps)
        if (directDomains.isNotEmpty()) {
            generated += JSONObject()
                .put("domain_suffix", JSONArray(directDomains))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        if (directApps.isNotEmpty()) {
            generated += JSONObject()
                .put("package_name", JSONArray(directApps.toList()))
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
