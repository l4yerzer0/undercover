package com.undercover.vpn.parser

import com.undercover.vpn.shared.parser.SubscriptionMetadataFetcher
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionMetadataFetcherTest {

    @Test
    fun testBase64ProfileTitle() {
        val headers = mapOf(
            "profile-title" to listOf("base64:8J+agCBWZWx2ZXQgVlBOIOKAlCAjMTEyODc0Mg=="),
            "profile-update-interval" to listOf("2"),
        )
        val metadata = SubscriptionMetadataFetcher.parseMetadata(headers)
        assertEquals("🚀 Velvet VPN — #1128742", metadata.title)
        assertEquals(2, metadata.updateIntervalHours)
    }

    @Test
    fun testUtf8EncodedProfileTitle() {
        val headers = mapOf(
            "profile-title" to listOf("utf-8''%F0%9F%9A%80%20My%20VPN"),
        )
        val metadata = SubscriptionMetadataFetcher.parseMetadata(headers)
        assertEquals("🚀 My VPN", metadata.title)
    }

    @Test
    fun testContentDispositionFallback() {
        val headers = mapOf(
            "content-disposition" to listOf("attachment; filename=\"Velvet VPN (#1128742)\""),
        )
        val metadata = SubscriptionMetadataFetcher.parseMetadata(headers)
        assertEquals("Velvet VPN (#1128742)", metadata.title)
    }

    @Test
    fun testUrlFragmentFallback() {
        val headers = emptyMap<String, List<String>>()
        val metadata = SubscriptionMetadataFetcher.parseMetadata(headers, url = "https://example.com/sub#My%20Custom%20VPN")
        assertEquals("My Custom VPN", metadata.title)
    }

    @Test
    fun testBrandNameFallback() {
        val headers = mapOf(
            "x-4net-brand-name" to listOf("Velvet VPN"),
        )
        val metadata = SubscriptionMetadataFetcher.parseMetadata(headers)
        assertEquals("Velvet VPN", metadata.title)
    }
}
