package com.undercover.vpn.routing

import android.util.Log
import com.undercover.vpn.database.Settings
import com.undercover.vpn.database.UserProfile
import com.undercover.vpn.database.UserProfileManager
import com.undercover.vpn.shared.parser.XrayConfigGenerator
import kotlinx.coroutines.runBlocking
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
    const val TAG_SECONDARY_OUT = "proxy-secondary"
    const val TAG_SECONDARY_DNS = "cloudflare-doh-secondary"

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

    val FALLBACK_RU_APPS = listOf(
        "ru.sberbankmobile",
        "com.idamob.tinkoff.android",
        "ru.vtb24.mobilebanking.android",
        "ru.alfabank.mobile.android",
        "ru.rostel",
        "ru.gosuslugi.pos",
        "ru.ozon.app.android",
        "com.wildberries.ru",
        "com.avito.android",
        "com.vkontakte.android",
        "ru.yandex.taxi",
        "ru.yandex.eda",
        "ru.yandex.market",
        "ru.yandex.searchplugin",
        "ru.kinopoisk",
        "ru.rutube.app",
        "ru.dublgis.dgismobile",
        "ru.oneme.app",
    )

    private val GENERATED_RULE_SET_TAGS = setOf(
        RuleSetManager.TAG_GEOSITE_RU,
        RuleSetManager.TAG_GEOIP_RU,
        RuleSetManager.TAG_RU_APPS,
    )

    /**
     * Returns [configJson] with generated outbounds and route rules.
     */
    fun apply(
        configJson: String,
        byeDpiPort: Int?,
        userProfile: UserProfile? = null,
        secondaryConfigJson: String? = null,
        secondaryNodeTag: String? = null,
        secondaryUsesXray: Boolean = false,
    ): String {
        val activeProfile = userProfile ?: runBlocking { UserProfileManager.getActiveProfile() }

        val directApps = activeProfile.directApps
        val byeDpiApps = if (byeDpiPort != null) {
            activeProfile.byeDpiApps
        } else {
            emptySet()
        }

        val directDomains = mutableListOf<String>()
        if (activeProfile.routingDirectRuSites) {
            directDomains.addAll(RU_DOMAINS_PRESET)
        }
        directDomains.addAll(activeProfile.directDomains)

        val directRuleSetFiles = mutableListOf<RuleSetManager.RuleSetSpec>()
        if (activeProfile.routingDirectRuSites) {
            RuleSetManager.specForTag(RuleSetManager.TAG_GEOSITE_RU)?.let {
                if (RuleSetManager.isAvailable(it.tag)) directRuleSetFiles.add(it)
            }
            RuleSetManager.specForTag(RuleSetManager.TAG_GEOIP_RU)?.let {
                if (RuleSetManager.isAvailable(it.tag)) directRuleSetFiles.add(it)
            }
        }

        var fallbackRuApps: List<String> = emptyList()
        if (activeProfile.routingDirectRuApps) {
            val ruAppsSpec = RuleSetManager.specForTag(RuleSetManager.TAG_RU_APPS)
            if (ruAppsSpec != null && RuleSetManager.isAvailable(ruAppsSpec.tag)) {
                directRuleSetFiles.add(ruAppsSpec)
            } else {
                fallbackRuApps = FALLBACK_RU_APPS
            }
        }

        val byeDpiDomains = mutableListOf<String>()
        if (byeDpiPort != null) {
            if (activeProfile.routingByeDpiYouTube) {
                byeDpiDomains.addAll(YOUTUBE_DOMAINS_PRESET)
            }
            if (activeProfile.routingByeDpiDiscord) {
                byeDpiDomains.addAll(DISCORD_DOMAINS_PRESET)
            }
            byeDpiDomains.addAll(activeProfile.byeDpiDomains)
        }

        val effectiveDirectApps = (directApps + fallbackRuApps).distinct()

        val dualActive = activeProfile.dualServerEnabled &&
            (secondaryUsesXray || (!secondaryConfigJson.isNullOrBlank()))

        val secondaryApps = if (dualActive) activeProfile.secondaryApps else emptySet()
        val secondaryDomains = if (dualActive) activeProfile.secondaryDomains else emptyList()

        return try {
            val config = JSONObject(configJson)
            stripGenerated(config)
            if (effectiveDirectApps.isNotEmpty() || directDomains.isNotEmpty() || directRuleSetFiles.isNotEmpty()) {
                ensureDirectOutbound(config)
            }
            if ((byeDpiApps.isNotEmpty() || byeDpiDomains.isNotEmpty()) && byeDpiPort != null) {
                ensureByeDpiOutbound(config, byeDpiPort)
            }
            if (dualActive) {
                ensureSecondaryOutbound(config, secondaryConfigJson, secondaryNodeTag, secondaryUsesXray)
                if (secondaryDomains.isNotEmpty()) {
                    ensureSecondaryDns(config)
                }
            }
            insertRules(
                config = config,
                directApps = effectiveDirectApps,
                byeDpiApps = byeDpiApps,
                directDomains = directDomains.distinct(),
                byeDpiDomains = byeDpiDomains.distinct(),
                directRuleSets = directRuleSetFiles,
                secondaryApps = secondaryApps,
                secondaryDomains = secondaryDomains.distinct(),
            )
            Log.i(
                TAG,
                "routing applied: directApps=${effectiveDirectApps.size} directDomains=${directDomains.size} " +
                    "directRuleSets=${directRuleSetFiles.map { it.tag }} " +
                    "byedpiApps=${byeDpiApps.size} byedpiDomains=${byeDpiDomains.size} " +
                    "dualActive=$dualActive secondaryApps=${secondaryApps.size} secondaryDomains=${secondaryDomains.size}",
            )
            config.toString()
        } catch (e: Exception) {
            Log.e(TAG, "failed to inject routing rules, running profile unmodified", e)
            configJson
        }
    }

    /**
     * Removes rules, rule_sets, and outbounds generated on an earlier run.
     */
    private fun stripGenerated(config: JSONObject) {
        val tags = (RoutingSection.entries.map { it.outboundTag } + TAG_SECONDARY_OUT).toSet()

        config.optJSONObject("route")?.let { route ->
            route.optJSONArray("rules")?.let { rules ->
                removeMatching(rules) { rule ->
                    val outbound = rule.optString("outbound")
                    val hasTarget = rule.has("package_name") || rule.has("domain_suffix") || rule.has("rule_set")
                    if (!hasTarget) return@removeMatching false
                    val isOurRoute = outbound in tags
                    val isOurReject = rule.optString("action") == "reject" &&
                        rule.optString("network") == "udp" &&
                        rule.optInt("port") == 443
                    isOurRoute || isOurReject
                }
            }
            route.optJSONArray("rule_set")?.let { ruleSets ->
                removeMatching(ruleSets) { it.optString("tag") in GENERATED_RULE_SET_TAGS }
            }
        }

        config.optJSONObject("dns")?.let { dns ->
            dns.optJSONArray("servers")?.let { servers ->
                removeMatching(servers) { it.optString("tag") == TAG_SECONDARY_DNS }
            }
            dns.optJSONArray("rules")?.let { rules ->
                removeMatching(rules) { it.optString("server") == TAG_SECONDARY_DNS }
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

    private fun ensureSecondaryOutbound(
        config: JSONObject,
        secondaryConfigJson: String?,
        secondaryNodeTag: String?,
        secondaryUsesXray: Boolean,
    ) {
        val outbounds = outbounds(config)
        if (findByTag(outbounds, TAG_SECONDARY_OUT) != null) return

        if (secondaryUsesXray) {
            val outbound = JSONObject()
            outbound.put("type", "socks")
            outbound.put("tag", TAG_SECONDARY_OUT)
            outbound.put("server", BYEDPI_ADDRESS)
            outbound.put("server_port", XrayConfigGenerator.LOCAL_SOCKS_PORT_SECONDARY)
            outbound.put("version", "5")
            outbounds.put(outbound)
        } else if (!secondaryConfigJson.isNullOrBlank()) {
            try {
                val secRoot = JSONObject(secondaryConfigJson)
                val secOutbounds = secRoot.optJSONArray("outbounds")
                var nodeOutbound: JSONObject? = null
                if (secOutbounds != null) {
                    for (i in 0 until secOutbounds.length()) {
                        val ob = secOutbounds.optJSONObject(i) ?: continue
                        val tag = ob.optString("tag")
                        val type = ob.optString("type").lowercase()
                        if (type in setOf("direct", "block", "dns", "selector", "urltest")) continue
                        if (secondaryNodeTag != null && tag == secondaryNodeTag) {
                            nodeOutbound = ob
                            break
                        }
                        if (nodeOutbound == null) {
                            nodeOutbound = ob
                        }
                    }
                }
                if (nodeOutbound != null) {
                    val cloned = JSONObject(nodeOutbound.toString())
                    cloned.put("tag", TAG_SECONDARY_OUT)
                    outbounds.put(cloned)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy secondary outbound", e)
            }
        }
    }

    private fun ensureSecondaryDns(config: JSONObject) {
        val dns = config.optJSONObject("dns") ?: return
        val servers = dns.optJSONArray("servers") ?: return
        var found = false
        for (i in 0 until servers.length()) {
            if (servers.optJSONObject(i)?.optString("tag") == TAG_SECONDARY_DNS) {
                found = true
                break
            }
        }
        if (!found) {
            servers.put(
                JSONObject()
                    .put("tag", TAG_SECONDARY_DNS)
                    .put("type", "https")
                    .put("server", "1.1.1.1")
                    .put("server_port", 443)
                    .put("detour", TAG_SECONDARY_OUT),
            )
        }
    }

    private fun insertRules(
        config: JSONObject,
        directApps: List<String>,
        byeDpiApps: Set<String>,
        directDomains: List<String>,
        byeDpiDomains: List<String>,
        directRuleSets: List<RuleSetManager.RuleSetSpec>,
        secondaryApps: Set<String>,
        secondaryDomains: List<String>,
    ) {
        val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }

        if (directRuleSets.isNotEmpty()) {
            val ruleSets = route.optJSONArray("rule_set") ?: JSONArray().also { route.put("rule_set", it) }
            for (spec in directRuleSets) {
                val path = RuleSetManager.filePath(spec.tag) ?: continue
                ruleSets.put(
                    JSONObject()
                        .put("tag", spec.tag)
                        .put("type", "local")
                        .put("format", "binary")
                        .put("path", path),
                )
            }
        }

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

        // 2. Direct rules (rule-sets, domains & apps)
        if (directRuleSets.isNotEmpty()) {
            generated += JSONObject()
                .put("rule_set", JSONArray(directRuleSets.map { it.tag }))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        if (directDomains.isNotEmpty()) {
            generated += JSONObject()
                .put("domain_suffix", JSONArray(directDomains))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        if (directApps.isNotEmpty()) {
            generated += JSONObject()
                .put("package_name", JSONArray(directApps))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        // 3. Secondary server rules (domains & apps)
        if (secondaryDomains.isNotEmpty()) {
            generated += JSONObject()
                .put("domain_suffix", JSONArray(secondaryDomains))
                .put("action", "route")
                .put("outbound", TAG_SECONDARY_OUT)
        }

        if (secondaryApps.isNotEmpty()) {
            generated += JSONObject()
                .put("package_name", JSONArray(secondaryApps.toList()))
                .put("action", "route")
                .put("outbound", TAG_SECONDARY_OUT)
        }

        val insertAt = leadingPreambleLength(rules)
        generated.forEachIndexed { offset, rule ->
            insertAt(rules, insertAt + offset, rule)
        }

        // 4. DNS rules for secondary domains
        val dns = config.optJSONObject("dns")
        val dnsRules = dns?.optJSONArray("rules")
        if (dnsRules != null && secondaryDomains.isNotEmpty()) {
            dnsRules.put(
                JSONObject()
                    .put("domain_suffix", JSONArray(secondaryDomains))
                    .put("server", TAG_SECONDARY_DNS),
            )
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
