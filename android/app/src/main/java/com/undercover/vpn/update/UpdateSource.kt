package com.undercover.vpn.update

enum class UpdateSource {
    GITHUB,
    FDROID,
    ;

    companion object {
        fun fromString(value: String): UpdateSource = when (value.lowercase()) {
            "fdroid" -> FDROID
            else -> GITHUB
        }
    }
}
