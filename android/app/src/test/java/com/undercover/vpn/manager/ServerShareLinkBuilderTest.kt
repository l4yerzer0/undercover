package com.undercover.vpn.manager

import com.undercover.vpn.shared.manager.share.ServerShareLinkBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerShareLinkBuilderTest {

    @Test
    fun testBuildVlessRealityLink() {
        val link = ServerShareLinkBuilder.buildVlessRealityLink(
            uuid = "11111111-2222-3333-4444-555555555555",
            host = "95.217.1.1",
            port = 443,
            publicKey = "w8d+K3vX5q6z7A==",
            serverName = "gateway.icloud.com",
            shortId = "1234abcd",
            alias = "Alice VPS"
        )

        assertTrue(link.startsWith("vless://11111111-2222-3333-4444-555555555555@95.217.1.1:443?"))
        assertTrue(link.contains("security=reality"))
        assertTrue(link.contains("flow=xtls-rprx-vision"))
        assertTrue(link.contains("fp=chrome"))
        assertTrue(link.contains("type=tcp"))
        assertTrue(link.contains("sni=gateway.icloud.com"))
        assertTrue(link.contains("sid=1234abcd"))
        assertTrue(link.endsWith("#Alice%20VPS") || link.endsWith("#Alice VPS"))
    }

    @Test
    fun testBuildHysteria2Link() {
        val link = ServerShareLinkBuilder.buildHysteria2Link(
            password = "supersecretpassword123",
            host = "1.2.3.4",
            port = 8443,
            serverName = "myvpn.example.com",
            insecure = true,
            pinSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            alias = "Bob Hysteria"
        )

        assertTrue(link.startsWith("hysteria2://supersecretpassword123@1.2.3.4:8443?"))
        assertTrue(link.contains("sni=myvpn.example.com"))
        assertTrue(link.contains("insecure=1"))
        assertTrue(link.contains("pinSHA256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
        assertTrue(link.endsWith("#Bob%20Hysteria") || link.endsWith("#Bob Hysteria"))
    }
}
