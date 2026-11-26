package com.undercover.vpn.shared.manager.share

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ServerShareLinkBuilder {

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
    }

    fun buildVlessRealityLink(
        uuid: String,
        host: String,
        port: Int,
        publicKey: String,
        serverName: String,
        shortId: String? = null,
        alias: String? = null
    ): String {
        val finalAlias = if (alias.isNullOrBlank()) "Undercover-$host" else alias
        val params = mutableListOf(
            "security=reality",
            "encryption=none",
            "pbk=${urlEncode(publicKey)}",
            "headerType=none",
            "fp=chrome",
            "type=tcp",
            "flow=xtls-rprx-vision",
            "sni=${urlEncode(serverName)}"
        )
        if (!shortId.isNullOrBlank()) {
            params.add("sid=${urlEncode(shortId)}")
        }

        val query = params.joinToString("&")
        val fragment = urlEncode(finalAlias)
        return "vless://$uuid@$host:$port?$query#$fragment"
    }

    fun buildHysteria2Link(
        password: String,
        host: String,
        port: Int,
        serverName: String,
        insecure: Boolean = true,
        pinSha256: String? = null,
        alias: String? = null
    ): String {
        val finalAlias = if (alias.isNullOrBlank()) "Undercover-$host" else alias
        val params = mutableListOf<String>()
        if (serverName.isNotBlank()) {
            params.add("sni=${urlEncode(serverName)}")
        }
        if (insecure) {
            params.add("insecure=1")
        }
        if (!pinSha256.isNullOrBlank()) {
            params.add("pinSHA256=${urlEncode(pinSha256)}")
        }

        val query = params.joinToString("&")
        val queryStr = if (query.isNotEmpty()) "?$query" else ""
        val fragment = urlEncode(finalAlias)
        return "hysteria2://$password@$host:$port$queryStr#$fragment"
    }
}
