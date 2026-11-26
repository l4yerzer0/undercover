package com.undercover.vpn.routing

import com.undercover.vpn.database.UserProfile
import com.undercover.vpn.shared.parser.XrayConfigGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSecurityProtectionTest {

    @Test
    fun testXrayBuildSocksInboundDefaultNoAuth() {
        val inbound = XrayConfigGenerator.buildSocksInbound(tag = "socks-test", port = 10808, auth = null)
        assertEquals("socks-test", inbound.getString("tag"))
        assertEquals(10808, inbound.getInt("port"))
        val settings = inbound.getJSONObject("settings")
        assertEquals("noauth", settings.getString("auth"))
        assertFalse(settings.has("accounts"))
    }

    @Test
    fun testXrayBuildSocksInboundWithAuth() {
        val auth = XrayConfigGenerator.XrayBridgeAuth(
            basePort = 45678,
            username = "secret_user",
            password = "secret_password_123"
        )
        val inbound = XrayConfigGenerator.buildSocksInbound(tag = "socks-auth-test", port = auth.basePort, auth = auth)
        assertEquals("socks-auth-test", inbound.getString("tag"))
        assertEquals(45678, inbound.getInt("port"))
        val settings = inbound.getJSONObject("settings")
        assertEquals("password", settings.getString("auth"))
        val accounts = settings.getJSONArray("accounts")
        assertEquals(1, accounts.length())
        val acc = accounts.getJSONObject(0)
        assertEquals("secret_user", acc.getString("user"))
        assertEquals("secret_password_123", acc.getString("pass"))
    }

    @Test
    fun testXrayCombineMultiWithAuth() {
        val vlessJson = JSONObject().apply {
            put("outbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "main")
                    put("protocol", "vless")
                })
            })
        }
        val auth = XrayConfigGenerator.XrayBridgeAuth(
            basePort = 31234,
            username = "u1",
            password = "p1"
        )
        val combinedStr = XrayConfigGenerator.combineMultiXrayConfigs(
            indexedConfigs = listOf(0 to vlessJson.toString()),
            basePort = auth.basePort,
            auth = auth
        )
        assertNotNull(combinedStr)
        val combined = JSONObject(combinedStr!!)
        val inbounds = combined.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        val inbound = inbounds.getJSONObject(0)
        assertEquals(31234, inbound.getInt("port"))
        val settings = inbound.getJSONObject("settings")
        assertEquals("password", settings.getString("auth"))
        val acc = settings.getJSONArray("accounts").getJSONObject(0)
        assertEquals("u1", acc.getString("user"))
        assertEquals("p1", acc.getString("pass"))
    }

    @Test
    fun testStripExposedInboundsRemovesProxyPortsWhenTunPresent() {
        val config = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                })
                put(JSONObject().apply {
                    put("type", "mixed")
                    put("listen", "127.0.0.1")
                    put("port", 2080)
                })
                put(JSONObject().apply {
                    put("type", "socks")
                    put("listen", "0.0.0.0")
                    put("port", 10808)
                })
                put(JSONObject().apply {
                    put("type", "http")
                    put("listen", "127.0.0.1")
                    put("port", 8080)
                })
            })
        }

        RoutingConfig.stripExposedInbounds(config)

        val inbounds = config.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("tun", inbounds.getJSONObject(0).getString("type"))
    }

    @Test
    fun testStripGeneratedRemovesExcludePackageFromTun() {
        val config = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("exclude_package", JSONArray(listOf("com.ru.bank", "com.yandex")))
                })
            })
        }

        RoutingConfig.stripGenerated(config)

        val tun = config.getJSONArray("inbounds").getJSONObject(0)
        assertFalse(tun.has("exclude_package"))
    }

    @Test
    fun testApplyMultiInjectsExcludePackageForDirectApps() {
        val baseConfig = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                })
            })
            put("outbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "socks")
                    put("tag", "socks-out")
                    put("server", "127.0.0.1")
                    put("server_port", 10808)
                })
            })
            put("route", JSONObject().apply {
                put("rules", JSONArray())
            })
        }

        val profile = UserProfile(name = "TestProfile").apply {
            directApps = setOf("com.bank.app", "ru.yandex.taxi")
            routingDirectRuSites = false
            routingDirectRuApps = false
        }

        val auth = XrayConfigGenerator.XrayBridgeAuth(
            basePort = 54321,
            username = "bridge_user",
            password = "bridge_pass"
        )

        val modifiedJson = RoutingConfig.applyMulti(
            configJson = baseConfig.toString(),
            byeDpiPort = null,
            userProfile = profile,
            xrayAuth = auth
        )

        val result = JSONObject(modifiedJson)
        val tun = result.getJSONArray("inbounds").getJSONObject(0)
        assertTrue(tun.has("exclude_package"))
        val excluded = tun.getJSONArray("exclude_package")
        val excludedList = (0 until excluded.length()).map { excluded.getString(it) }
        assertTrue(excludedList.contains("com.bank.app"))
        assertTrue(excludedList.contains("ru.yandex.taxi"))

        // Check socks outbound updated with dynamic auth
        val socksOb = result.getJSONArray("outbounds").getJSONObject(0)
        assertEquals(54321, socksOb.getInt("server_port"))
        assertEquals("bridge_user", socksOb.getString("username"))
        assertEquals("bridge_pass", socksOb.getString("password"))
    }

    @Test
    fun testApplyMultiDoesNotSetExcludePackageWhenIncludePackagePresent() {
        val baseConfig = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("include_package", JSONArray(listOf("com.telegram.messenger")))
                })
            })
            put("outbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "direct")
                })
            })
            put("route", JSONObject().apply {
                put("rules", JSONArray())
            })
        }

        val profile = UserProfile(name = "TestProfile").apply {
            directApps = setOf("com.bank.app")
            routingDirectRuSites = false
            routingDirectRuApps = false
        }

        val modifiedJson = RoutingConfig.applyMulti(
            configJson = baseConfig.toString(),
            byeDpiPort = null,
            userProfile = profile
        )

        val result = JSONObject(modifiedJson)
        val tun = result.getJSONArray("inbounds").getJSONObject(0)
        // Since include_package is present, exclude_package must NOT be set (OS limitation: cannot mix)
        assertFalse(tun.has("exclude_package"))
    }

    @Test
    fun testApplyMultiInjectsByeDpiAuthIntoOutbound() {
        val baseConfig = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                })
            })
            put("outbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "direct")
                })
            })
            put("route", JSONObject().apply {
                put("rules", JSONArray())
            })
        }

        val auth = com.undercover.vpn.byedpi.ByeDpiManager.ByeDpiAuth(
            username = "byedpi_user",
            password = "byedpi_password_xyz"
        )

        val profile = UserProfile(name = "TestProfile").apply {
            byeDpiApps = setOf("com.google.android.youtube")
            routingByeDpiYouTube = true
            routingDirectRuSites = false
            routingDirectRuApps = false
        }

        val modifiedJson = RoutingConfig.applyMulti(
            configJson = baseConfig.toString(),
            byeDpiPort = 40459,
            userProfile = profile,
            byeDpiAuth = auth,
            isProMode = false
        )

        val result = JSONObject(modifiedJson)
        val outbounds = result.getJSONArray("outbounds")
        var foundByeDpi = false
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.getJSONObject(i)
            if (ob.optString("tag") == RoutingSection.BYEDPI.outboundTag) {
                foundByeDpi = true
                assertEquals("socks", ob.getString("type"))
                assertEquals(40459, ob.getInt("server_port"))
                assertEquals("byedpi_user", ob.getString("username"))
                assertEquals("byedpi_password_xyz", ob.getString("password"))
            }
        }
        assertTrue("byedpi outbound must be present", foundByeDpi)
    }
}
