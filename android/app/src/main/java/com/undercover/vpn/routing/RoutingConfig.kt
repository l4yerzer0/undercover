package com.undercover.vpn.routing

import android.util.Log
import com.undercover.vpn.database.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates the per-app routing rules from the UI selection and injects them into
 * the profile on its way to the core.
 *
 * The profile on disk is never touched: rules are added to an in-memory copy at
 * every start and reload, so the user's JSON stays theirs and nothing
 * accumulates across restarts. That also lets the ByeDPI outbound carry a port
 * that is only known at runtime.
 *
 * Split tunnelling is done entirely here rather than through
 * `VpnService.addDisallowedApplication` — see docs/per-app-bypass.md for why.
 */
object RoutingConfig {
    private const val TAG = "RoutingConfig"
    private const val BYEDPI_ADDRESS = "127.0.0.1"

    /**
     * Returns [configJson] with generated outbounds and route rules.
     *
     * [byeDpiPort] is the port the embedded proxy is listening on, or null when
     * ByeDPI is off or failed to start; in that case the ByeDPI section is
     * skipped entirely and its apps fall through to the profile's own rules.
     */
    fun apply(configJson: String, byeDpiPort: Int?): String {
        val direct = Settings.routingList(RoutingSection.DIRECT)
        val byeDpi = if (byeDpiPort != null) {
            Settings.routingList(RoutingSection.BYEDPI)
        } else {
            emptySet()
        }

        return try {
            val config = JSONObject(configJson)
            stripGenerated(config)
            if (direct.isNotEmpty()) ensureDirectOutbound(config)
            if (byeDpi.isNotEmpty()) ensureByeDpiOutbound(config, byeDpiPort!!)
            insertRules(config, direct, byeDpi)
            Log.i(TAG, "routing applied: direct=${direct.size} byedpi=${byeDpi.size}")
            config.toString()
        } catch (e: Exception) {
            // A malformed profile is the user's to fix; running it unmodified gives
            // them the core's own error rather than one from us.
            Log.e(TAG, "failed to inject routing rules, running profile unmodified", e)
            configJson
        }
    }

    /**
     * Removes rules and outbounds this object generated on an earlier run, so that
     * regenerating is idempotent and unassigning an app in the UI actually drops
     * its rule from the profile.
     *
     * Generated rules are recognised by shape rather than by a marker: sing-box
     * rejects unknown fields in rules, so there is nowhere to stamp one. A rule
     * counts as ours when it carries `package_name` together with one of our
     * outbound tags, or is the QUIC reject we emit alongside the ByeDPI rule. A
     * hand-written rule of exactly that shape would be swallowed — the profile is
     * generated from the UI, so that is the intended trade.
     */
    private fun stripGenerated(config: JSONObject) {
        val tags = RoutingSection.entries.map { it.outboundTag }.toSet()

        config.optJSONObject("route")?.optJSONArray("rules")?.let { rules ->
            removeMatching(rules) { rule ->
                if (!rule.has("package_name")) return@removeMatching false
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

    private fun insertRules(config: JSONObject, direct: Set<String>, byeDpi: Set<String>) {
        val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }

        val generated = mutableListOf<JSONObject>()

        if (byeDpi.isNotEmpty()) {
            // ByeDPI desyncs a TCP stream; over SOCKS5 UDP none of that applies. Deny
            // QUIC for these apps so they fall back to TCP instead of silently
            // losing the bypass.
            generated += JSONObject()
                .put("package_name", JSONArray(byeDpi.toList()))
                .put("network", "udp")
                .put("port", 443)
                .put("action", "reject")
            generated += JSONObject()
                .put("package_name", JSONArray(byeDpi.toList()))
                .put("action", "route")
                .put("outbound", RoutingSection.BYEDPI.outboundTag)
        }

        if (direct.isNotEmpty()) {
            generated += JSONObject()
                .put("package_name", JSONArray(direct.toList()))
                .put("action", "route")
                .put("outbound", RoutingSection.DIRECT.outboundTag)
        }

        // Generated rules go after the profile's leading sniff/hijack-dns rules —
        // those must keep running first — but ahead of any user rules, so an app
        // assigned in the UI wins over a broader rule in the profile.
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
