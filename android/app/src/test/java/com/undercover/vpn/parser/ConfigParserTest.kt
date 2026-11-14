package com.undercover.vpn.parser

import com.undercover.vpn.shared.parser.ConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigParserTest {

    @Test
    fun testIsSupportedUri() {
        assertTrue(ConfigParser.isSupportedUri("vless://12345@example.com:443"))
        assertTrue(ConfigParser.isSupportedUri("vmess://eyJhZGQiOiIxLmMifQ=="))
        assertTrue(ConfigParser.isSupportedUri("ss://YWVzLTEyOC1nY206cGFzc3dvcmQ=@192.168.1.1:8388#Test"))
        assertTrue(ConfigParser.isSupportedUri("trojan://pass@trojan.com:443"))
        assertTrue(ConfigParser.isSupportedUri("hysteria2://pass@hy2.com:443"))
        assertTrue(ConfigParser.isSupportedUri("hy2://pass@hy2.com:443"))

        assertFalse(ConfigParser.isSupportedUri("https://google.com"))
        assertFalse(ConfigParser.isSupportedUri("ftp://server.com"))
        assertFalse(ConfigParser.isSupportedUri("not_a_valid_uri"))
        assertFalse(ConfigParser.isSupportedUri(""))
    }

    @Test
    fun testParseVlessName() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@server.undercover.io:443?type=tcp&security=reality#Tactical-Node-1"
        val name = ConfigParser.parseUriToName(uri)
        assertEquals("Tactical-Node-1", name)
    }

    @Test
    fun testParseVlessWithCyrillicAndSpaces() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@server.undercover.io:443?type=tcp&security=reality#Германия Быстрый [VIP]"
        val name = ConfigParser.parseUriToName(uri)
        assertEquals("Германия Быстрый [VIP]", name)
    }

    @Test
    fun testParseTrojanName() {
        val uri = "trojan://secret-password@vpn.undercover.org:443#Dossier-Cover"
        val name = ConfigParser.parseUriToName(uri)
        assertEquals("Dossier-Cover", name)
    }

    @Test
    fun testParseInvalidUriReturnsNull() {
        val name = ConfigParser.parseUriToName("invalid://foo#bar")
        assertNull(name)
    }
}
