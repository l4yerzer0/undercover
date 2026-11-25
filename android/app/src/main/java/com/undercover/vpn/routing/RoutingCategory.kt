package com.undercover.vpn.routing

import com.undercover.vpn.R

enum class RoutingCategory(
    val titleRes: Int,
    val descRes: Int,
    val ruleSetTags: List<String>,
    val fallbackDomains: List<String> = emptyList(),
) {
    AI(
        titleRes = R.string.routing_cat_ai,
        descRes = R.string.routing_cat_ai_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_AI),
        fallbackDomains = listOf("openai.com", "chatgpt.com", "anthropic.com", "claude.ai", "gemini.google.com"),
    ),
    YOUTUBE(
        titleRes = R.string.routing_cat_youtube,
        descRes = R.string.routing_cat_youtube_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_YOUTUBE),
        fallbackDomains = RoutingConfig.YOUTUBE_DOMAINS_PRESET,
    ),
    DISCORD(
        titleRes = R.string.routing_cat_discord,
        descRes = R.string.routing_cat_discord_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_DISCORD),
        fallbackDomains = RoutingConfig.DISCORD_DOMAINS_PRESET,
    ),
    STREAMING(
        titleRes = R.string.routing_cat_streaming,
        descRes = R.string.routing_cat_streaming_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_STREAMING),
        fallbackDomains = listOf("spotify.com", "netflix.com", "twitch.tv"),
    ),
    GAMES(
        titleRes = R.string.routing_cat_games,
        descRes = R.string.routing_cat_games_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_GAMES),
        fallbackDomains = listOf("steampowered.com", "steamcommunity.com", "epicgames.com", "playstation.com"),
    ),
    SOCIAL(
        titleRes = R.string.routing_cat_social,
        descRes = R.string.routing_cat_social_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_SOCIAL),
        fallbackDomains = listOf("instagram.com", "x.com", "twitter.com", "facebook.com", "tiktok.com"),
    ),
    RU_SERVICES(
        titleRes = R.string.routing_cat_ru,
        descRes = R.string.routing_cat_ru_desc,
        ruleSetTags = listOf(RuleSetManager.TAG_GEOSITE_RU, RuleSetManager.TAG_GEOIP_RU, RuleSetManager.TAG_RU_APPS),
        fallbackDomains = RoutingConfig.RU_DOMAINS_PRESET,
    );

    companion object {
        fun fromName(name: String?): RoutingCategory? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
