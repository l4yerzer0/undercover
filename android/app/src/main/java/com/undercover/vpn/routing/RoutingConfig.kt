package com.undercover.vpn.routing

import android.util.Log
import com.undercover.vpn.database.RoutingChannel
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

    private val GENERATED_RULE_SET_TAGS = RuleSetManager.SPECS.map { it.tag }.toSet()

    data class ResolvedChannel(
        val slotIndex: Int,
        val channel: RoutingChannel,
        val configJson: String?,
        val usesXray: Boolean,
    ) {
        val outboundTag: String = "proxy-channel-$slotIndex"
        val dnsTag: String = "cloudflare-doh-channel-$slotIndex"
    }

    /**
     * Legacy dual-server entry point delegating to [applyMulti].
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
        val resolvedChannels = if (Settings.isProMode && activeProfile.dualServerEnabled &&
            (secondaryUsesXray || !secondaryConfigJson.isNullOrBlank())
        ) {
            listOf(
                ResolvedChannel(
                    slotIndex = 1,
                    channel = com.undercover.vpn.database.RoutingChannel(
                        id = "chan_1",
                        serverId = activeProfile.secondaryServerId,
                        nodeTag = secondaryNodeTag,
                        apps = activeProfile.secondaryApps,
                        domains = activeProfile.secondaryDomains,
                    ),
                    configJson = secondaryConfigJson,
                    usesXray = secondaryUsesXray,
                ),
            )
        } else {
            emptyList()
        }
        return applyMulti(configJson, byeDpiPort, activeProfile, resolvedChannels)
    }

    /**
     * Returns [configJson] with generated outbounds, category rule-sets, and route rules for N channels.
     */
    fun applyMulti(
        configJson: String,
        byeDpiPort: Int?,
        userProfile: UserProfile? = null,
        channels: List<ResolvedChannel> = emptyList(),
    ): String {
        val activeProfile = userProfile ?: runBlocking { UserProfileManager.getActiveProfile() }
        val isPro = Settings.isProMode

        val directApps = activeProfile.directApps
        val byeDpiApps = if (byeDpiPort != null) activeProfile.byeDpiApps else emptySet()

        val directDomains = mutableListOf<String>()
        if (activeProfile.routingDirectRuSites) {
            directDomains.addAll(RU_DOMAINS_PRESET)
        }
        directDomains.addAll(activeProfile.directDomains)

        val allRuleSetSpecs = LinkedHashMap<String, RuleSetManager.RuleSetSpec>()
        val directRuleSetTags = mutableListOf<String>()

        if (activeProfile.routingDirectRuSites) {
            listOf(RuleSetManager.TAG_GEOSITE_RU, RuleSetManager.TAG_GEOIP_RU).forEach { tag ->
                RuleSetManager.specForTag(tag)?.let { spec ->
                    if (RuleSetManager.isAvailable(spec.tag)) {
                        allRuleSetSpecs[spec.tag] = spec
                        directRuleSetTags.add(spec.tag)
                    }
                }
            }
        }

        var fallbackRuApps: List<String> = emptyList()
        if (activeProfile.routingDirectRuApps) {
            val ruAppsSpec = RuleSetManager.specForTag(RuleSetManager.TAG_RU_APPS)
            if (ruAppsSpec != null && RuleSetManager.isAvailable(ruAppsSpec.tag)) {
                allRuleSetSpecs[ruAppsSpec.tag] = ruAppsSpec
                directRuleSetTags.add(ruAppsSpec.tag)
            } else {
                fallbackRuApps = FALLBACK_RU_APPS
            }
        }

        val byeDpiDomains = mutableListOf<String>()
        if (byeDpiPort != null) {
            val fallbackVpnYoutube = com.undercover.vpn.byedpi.ByeDpiRuntimeState.isYouTubeVpnFallback
            val fallbackVpnDiscord = com.undercover.vpn.byedpi.ByeDpiRuntimeState.isDiscordVpnFallback
            if (activeProfile.routingByeDpiYouTube && !fallbackVpnYoutube) {
                byeDpiDomains.addAll(YOUTUBE_DOMAINS_PRESET)
            }
            if (activeProfile.routingByeDpiDiscord && !fallbackVpnDiscord) {
                byeDpiDomains.addAll(DISCORD_DOMAINS_PRESET)
            }
            byeDpiDomains.addAll(activeProfile.byeDpiDomains)
        }

        val effectiveDirectApps = (directApps + fallbackRuApps).distinct()
        val activeChannels = if (isPro) {
            channels.filter { it.usesXray || !it.configJson.isNullOrBlank() }
        } else {
            emptyList()
        }

        return try {
            val config = JSONObject(configJson)
            stripGenerated(config)

            if (effectiveDirectApps.isNotEmpty() || directDomains.isNotEmpty() || directRuleSetTags.isNotEmpty()) {
                ensureDirectOutbound(config)
            }
            if ((byeDpiApps.isNotEmpty() || byeDpiDomains.isNotEmpty()) && byeDpiPort != null) {
                ensureByeDpiOutbound(config, byeDpiPort)
            }

            data class PreparedChannel(
                val resolved: ResolvedChannel,
                val apps: List<String>,
                val domains: List<String>,
                val ruleSetTags: List<String>,
            )

            val preparedChannels = activeChannels.map { rc ->
                val chDomains = rc.channel.domains.toMutableList()
                val chRuleSets = mutableListOf<String>()
                for (catName in rc.channel.categories) {
                    val cat = RoutingCategory.fromName(catName) ?: continue
                    var anySpecAvailable = false
                    for (tag in cat.ruleSetTags) {
                        val spec = RuleSetManager.specForTag(tag)
                        if (spec != null && RuleSetManager.isAvailable(spec.tag)) {
                            allRuleSetSpecs[spec.tag] = spec
                            chRuleSets.add(spec.tag)
                            anySpecAvailable = true
                        }
                    }
                    if (!anySpecAvailable && cat.fallbackDomains.isNotEmpty()) {
                        chDomains.addAll(cat.fallbackDomains)
                    }
                }
                ensureChannelOutbound(config, rc)
                if (chDomains.isNotEmpty() || chRuleSets.isNotEmpty()) {
                    ensureChannelDns(config, rc)
                }
                PreparedChannel(
                    resolved = rc,
                    apps = rc.channel.apps.toList(),
                    domains = chDomains.distinct(),
                    ruleSetTags = chRuleSets.distinct(),
                )
            }

            val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
            val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }

            if (allRuleSetSpecs.isNotEmpty()) {
                val ruleSets = route.optJSONArray("rule_set") ?: JSONArray().also { route.put("rule_set", it) }
                for (spec in allRuleSetSpecs.values) {
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
            val dnsRules = config.optJSONObject("dns")?.optJSONArray("rules")

            // 1. Channel explicit rules (apps, domains, categories) — highest user priority
            for (pc in preparedChannels) {
                val tag = pc.resolved.outboundTag
                if (pc.apps.isNotEmpty()) {
                    generated += JSONObject()
                        .put("package_name", JSONArray(pc.apps))
                        .put("action", "route")
                        .put("outbound", tag)
                }
                if (pc.domains.isNotEmpty()) {
                    generated += JSONObject()
                        .put("domain_suffix", JSONArray(pc.domains))
                        .put("action", "route")
                        .put("outbound", tag)
                    dnsRules?.put(
                        JSONObject()
                            .put("domain_suffix", JSONArray(pc.domains))
                            .put("server", pc.resolved.dnsTag),
                    )
                }
                if (pc.ruleSetTags.isNotEmpty()) {
                    generated += JSONObject()
                        .put("rule_set", JSONArray(pc.ruleSetTags))
                        .put("action", "route")
                        .put("outbound", tag)
                    dnsRules?.put(
                        JSONObject()
                            .put("rule_set", JSONArray(pc.ruleSetTags))
                            .put("server", pc.resolved.dnsTag),
                    )
                }
            }

            // 2. ByeDPI rules
            if (byeDpiDomains.isNotEmpty()) {
                val distinctByeDomains = byeDpiDomains.distinct()
                generated += JSONObject()
                    .put("domain_suffix", JSONArray(distinctByeDomains))
                    .put("network", "udp")
                    .put("port", 443)
                    .put("action", "reject")
                generated += JSONObject()
                    .put("domain_suffix", JSONArray(distinctByeDomains))
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

            // 3. Direct rules
            if (directRuleSetTags.isNotEmpty()) {
                generated += JSONObject()
                    .put("rule_set", JSONArray(directRuleSetTags.distinct()))
                    .put("action", "route")
                    .put("outbound", RoutingSection.DIRECT.outboundTag)
            }
            if (directDomains.isNotEmpty()) {
                generated += JSONObject()
                    .put("domain_suffix", JSONArray(directDomains.distinct()))
                    .put("action", "route")
                    .put("outbound", RoutingSection.DIRECT.outboundTag)
            }
            if (effectiveDirectApps.isNotEmpty()) {
                generated += JSONObject()
                    .put("package_name", JSONArray(effectiveDirectApps))
                    .put("action", "route")
                    .put("outbound", RoutingSection.DIRECT.outboundTag)
            }

            val insertAt = leadingPreambleLength(rules)
            generated.forEachIndexed { offset, rule ->
                insertAt(rules, insertAt + offset, rule)
            }

            Log.i(
                TAG,
                "routing applied (isPro=$isPro): directApps=${effectiveDirectApps.size} " +
                    "byedpiApps=${byeDpiApps.size} channels=${preparedChannels.size}",
            )
            config.toString()
        } catch (e: Exception) {
            Log.e(TAG, "failed to inject routing rules, running profile unmodified", e)
            configJson
        }
    }

    private fun isGeneratedOutbound(tag: String): Boolean {
        return tag in RoutingSection.entries.map { it.outboundTag } ||
            tag == TAG_SECONDARY_OUT ||
            tag.startsWith("proxy-channel-")
    }

    private fun isGeneratedDns(tag: String): Boolean {
        return tag == TAG_SECONDARY_DNS || tag.startsWith("cloudflare-doh-channel-")
    }

    /**
     * Removes rules, rule_sets, and outbounds generated on an earlier run.
     */
    private fun stripGenerated(config: JSONObject) {
        config.optJSONObject("route")?.let { route ->
            route.optJSONArray("rules")?.let { rules ->
                removeMatching(rules) { rule ->
                    val outbound = rule.optString("outbound")
                    val hasTarget = rule.has("package_name") || rule.has("domain_suffix") || rule.has("rule_set")
                    if (!hasTarget) return@removeMatching false
                    val isOurRoute = isGeneratedOutbound(outbound)
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
                removeMatching(servers) { isGeneratedDns(it.optString("tag")) }
            }
            dns.optJSONArray("rules")?.let { rules ->
                removeMatching(rules) { isGeneratedDns(it.optString("server")) }
            }
        }

        config.optJSONArray("outbounds")?.let { outbounds ->
            removeMatching(outbounds) { isGeneratedOutbound(it.optString("tag")) }
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

    private fun ensureChannelOutbound(config: JSONObject, resolved: ResolvedChannel) {
        val outbounds = outbounds(config)
        if (findByTag(outbounds, resolved.outboundTag) != null) return

        if (resolved.usesXray) {
            val outbound = JSONObject()
            outbound.put("type", "socks")
            outbound.put("tag", resolved.outboundTag)
            outbound.put("server", BYEDPI_ADDRESS)
            outbound.put("server_port", XrayConfigGenerator.LOCAL_SOCKS_PORT + resolved.slotIndex)
            outbound.put("version", "5")
            outbounds.put(outbound)
        } else if (!resolved.configJson.isNullOrBlank()) {
            try {
                val secRoot = JSONObject(resolved.configJson)
                val secOutbounds = secRoot.optJSONArray("outbounds")
                var nodeOutbound: JSONObject? = null
                val targetTag = resolved.channel.nodeTag
                if (secOutbounds != null) {
                    for (i in 0 until secOutbounds.length()) {
                        val ob = secOutbounds.optJSONObject(i) ?: continue
                        val tag = ob.optString("tag")
                        val type = ob.optString("type").lowercase()
                        if (type in setOf("direct", "block", "dns", "selector", "urltest")) continue
                        if (targetTag != null && tag == targetTag) {
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
                    cloned.put("tag", resolved.outboundTag)
                    outbounds.put(cloned)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy channel outbound ${resolved.outboundTag}", e)
            }
        }
    }

    private fun ensureChannelDns(config: JSONObject, resolved: ResolvedChannel) {
        val dns = config.optJSONObject("dns") ?: return
        val servers = dns.optJSONArray("servers") ?: return
        var found = false
        for (i in 0 until servers.length()) {
            if (servers.optJSONObject(i)?.optString("tag") == resolved.dnsTag) {
                found = true
                break
            }
        }
        if (!found) {
            servers.put(
                JSONObject()
                    .put("tag", resolved.dnsTag)
                    .put("type", "https")
                    .put("server", "1.1.1.1")
                    .put("server_port", 443)
                    .put("detour", resolved.outboundTag),
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
