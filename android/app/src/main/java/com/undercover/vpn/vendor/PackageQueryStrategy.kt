package com.undercover.vpn.vendor

sealed class PackageQueryStrategy {
    data object ForcedRoot : PackageQueryStrategy()
    data class UserSelected(val mode: String) : PackageQueryStrategy()
    data object Direct : PackageQueryStrategy()
}
