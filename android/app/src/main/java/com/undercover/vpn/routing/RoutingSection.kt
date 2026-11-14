package com.undercover.vpn.routing

/**
 * A destination apps can be assigned to in the routing UI.
 *
 * Each section maps to one sing-box outbound tag. Adding a section here plus a
 * list in Settings and a branch in [RoutingConfig] is all a new destination
 * needs — the app picker and the rule generator are already generic over this.
 */
enum class RoutingSection(val outboundTag: String) {
    /** Straight out over the underlying network, bypassing the tunnel. */
    DIRECT("direct-out"),

    /** Through the embedded ByeDPI proxy, which then goes out directly. */
    BYEDPI("byedpi-out"),
    ;

    companion object {
        fun fromName(value: String?): RoutingSection =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DIRECT
    }
}
