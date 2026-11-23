package com.undercover.vpn.parser

import com.undercover.vpn.shared.parser.ConfigParser
import com.undercover.vpn.shared.parser.SubscriptionContentParser
import com.undercover.vpn.shared.parser.SubscriptionNodesHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubscriptionNodesHelperTest {

    @Test
    fun testExtractAndSelectFromUriListSubscription() {
        val uris = """
            vless://11111111-1111-1111-1111-111111111111@de.example.com:443?type=tcp&security=tls#Germany
            vless://22222222-2222-2222-2222-222222222222@nl.example.com:443?type=tcp&security=tls#Netherlands
            hy2://pass@fi.example.com:443#Finland
        """.trimIndent()

        val json = SubscriptionContentParser.toSingBoxConfig(uris)
        assertNotNull(json)

        val info = SubscriptionNodesHelper.extractNodesInfo(json!!)
        assertEquals("proxy", info.selectorTag)
        assertEquals("Germany", info.selectedNodeTag)
        assertEquals(3, info.nodes.size)
        assertEquals("Germany", info.nodes[0].tag)
        assertEquals("VLESS", info.nodes[0].type)
        assertEquals("Netherlands", info.nodes[1].tag)
        assertEquals("Finland", info.nodes[2].tag)
        assertEquals("HYSTERIA2", info.nodes[2].type)

        val updatedJson = SubscriptionNodesHelper.selectNodeInConfig(json, "proxy", "Finland")
        assertNotNull(updatedJson)
        val updatedInfo = SubscriptionNodesHelper.extractNodesInfo(updatedJson!!)
        assertEquals("Finland", updatedInfo.selectedNodeTag)
    }

    @Test
    fun testExtractWithRouteFinalSelector() {
        val json = """
            {
              "outbounds": [
                { "type": "selector", "tag": "ads", "outbounds": ["block", "direct"] },
                { "type": "selector", "tag": "🚀 Velvet VPN", "outbounds": ["auto", "🇩🇪 DE", "🇺🇸 US"], "default": "🇩🇪 DE" },
                { "type": "urltest", "tag": "auto", "outbounds": ["🇩🇪 DE", "🇺🇸 US"] },
                { "type": "vless", "tag": "🇩🇪 DE", "server": "1.2.3.4", "server_port": 443 },
                { "type": "vless", "tag": "🇺🇸 US", "server": "5.6.7.8", "server_port": 443 },
                { "type": "direct", "tag": "direct" },
                { "type": "block", "tag": "block" }
              ],
              "route": { "final": "🚀 Velvet VPN" }
            }
        """.trimIndent()

        val info = SubscriptionNodesHelper.extractNodesInfo(json)
        assertEquals("🚀 Velvet VPN", info.selectorTag)
        assertEquals("🇩🇪 DE", info.selectedNodeTag)
        assertEquals(3, info.nodes.size)
        assertEquals("AUTO", info.nodes[0].type)
        assertEquals("🇩🇪 DE", info.nodes[1].tag)
        assertEquals("1.2.3.4", info.nodes[1].server)
        assertEquals(443, info.nodes[1].port)
    }
}
