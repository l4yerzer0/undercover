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
    fun testParseHysteria2Name() {
        val uri = "hysteria2://my-secret-key@198.51.100.1:443?sni=www.cloudflare.com&insecure=0#Undercover-Hysteria"
        val name = ConfigParser.parseUriToName(uri)
        assertEquals("Undercover-Hysteria", name)
    }

    @Test
    fun testParseInvalidUriReturnsNull() {
        val name = ConfigParser.parseUriToName("invalid://foo#bar")
        assertNull(name)
    }

    @Test
    fun testHappVlessEncryptedImport() {
        // happ://crypt1 link encrypting a vless:// proxy
        val happCrypt1 = "happ://crypt/Ht9gc8AokyXuqmhE16v2ekkfz7DeyuxdmRWz5g8pqA3cm023kOzyZAJMWl/fNGVz9VmPyc01dPX4yfptatQ0ZcicVch4mqRy2A/d07FLCTQBowjfM4X15pBGF0pvz3AOsrqjTmgHkecN7Ffs2JYjRcmk/DLGLPAPcvB2y2V3Qpw="
        assertTrue(ConfigParser.isSupportedUri(happCrypt1))

        val name1 = ConfigParser.parseUriToName(happCrypt1)
        assertEquals("Happ-Proxy-Node", name1)

        val json1 = ConfigParser.parseUriToSingBoxJson(happCrypt1)
        assertNotNull(json1)
        assertTrue(json1!!.contains("server.undercover.io"))

        // happ://crypt5 link encrypting a vless:// proxy
        val happCrypt5 = "happ://crypt5/fzvdqSwT3e9LcqcFA4qnUHtJ220qff0:FlilrloDlSKAflATyszsPl7wK+Ndg2bwiZuu71Qlt57eSIBwwvz9stEpKnFaJxxQGpnnWRFVOZZEv9qAO18NKJIpWVyOEv/v7vPVE8k8cEAg9D2r7Wp290s0MKoqcaOqm2Kh5P+SgptsjuonQAR4yxZqJlNwOw5juTrXN/sFY8MgjK70+DNkcsLUZ9AA14Y/j5UCr+rhN/ooilFKUJPWhuqreVw=kAv7sRUSSb+nKx91Phny+9Eb0Kcrp7N4s+mBMYE//xZPwiQt433/gkLd4M9bni+U/eB1klfz+YnSnM6T7noeoYy+XqonlJaRzY7jxA3Jvrg7Wq6y9zRbOWwzFd/LnQjLBHh/yxDIyqDgcPe4QGbvHmuYIReolhFSvf0PM9g7ia7QIb66WV6IWBZHmPMYDBBzfnYghAVwonFpFwuk7VFqvTx5sbD6vSF9KSvMC8b6TWGV7ZmL+YzayG2v7QHpQOexns8sAASLFq0j3cLgxAF7UNvfAumsYJ90gdu963ubgiQ7bfa+CrRQo9dbkhmXarZzg2/eTCRy5fQc0CaU4/NfOma4qq2qBTyXXKmnfCD1yUSFVx/kNTMKvEiTLZaUWOfihQwXI9d2FDOAuZ4qNRoBZv7up3jUxuH8uKfyz4WPJ1WJc7lt+vqV0dP9sOm3cmXTjv7oKtb2+Hojz5XBYnThxtgzUXxcKYxpg8hhr/P0j087W+9QfdX9NqKHEkLykRiUgHWEhV8YJZ/30U2tnEz0W1pBjv3I3pZB+EsEYuZMz8/fHyqR7Is50b75EcUS0hIEcclwIittLFI9UAo1o53K3kKEwNEEoPaCo8JPrhnTCjSf8sYe0ad+zdnuGexPjgM+3zZEzdh0QJPtX2FJjWKEnAWX1rFgJcKQJqo76rfkfoQ=ff"
        assertTrue(ConfigParser.isSupportedUri(happCrypt5))

        val name5 = ConfigParser.parseUriToName(happCrypt5)
        assertEquals("Happ-Proxy-Node", name5)

        val json5 = ConfigParser.parseUriToSingBoxJson(happCrypt5)
        assertNotNull(json5)
        assertTrue(json5!!.contains("server.undercover.io"))

        // happ://add/ wrapping plain vless
        val wrappedPlain = "happ://add/vless%3A%2F%2Fb831381d-6324-4d53-ad4f-8cda48b30811%40server.undercover.io%3A443%3Ftype%3Dtcp%26security%3Dreality%23Wrapped-Plain-Node"
        assertTrue(ConfigParser.isSupportedUri(wrappedPlain))
        assertEquals("Wrapped-Plain-Node", ConfigParser.parseUriToName(wrappedPlain))
    }
}
