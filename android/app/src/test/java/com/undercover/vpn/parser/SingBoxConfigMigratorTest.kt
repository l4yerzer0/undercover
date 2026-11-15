package com.undercover.vpn.parser

import com.undercover.vpn.shared.parser.SingBoxConfigMigrator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigMigratorTest {

    @Test
    fun testMigrateLegacyFakeIpWithServer() {
        val legacyConfig = """
            {
              "dns": {
                "servers": [
                  {
                    "tag": "fakeip-dns",
                    "address": "fakeip"
                  },
                  {
                    "tag": "remote-dns",
                    "address": "https://1.1.1.1/dns-query",
                    "detour": "proxy"
                  }
                ],
                "fakeip": {
                  "enabled": true,
                  "inet4_range": "198.18.0.0/15",
                  "inet6_range": "fc00::/18"
                },
                "independent_cache": true
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val dns = root.getJSONObject("dns")

        // 1. Root fakeip and independent_cache must be removed
        assertFalse(dns.has("fakeip"))
        assertFalse(dns.has("independent_cache"))

        // 2. Servers must be migrated
        val servers = dns.getJSONArray("servers")
        assertEquals(2, servers.length())

        val fakeServer = servers.getJSONObject(0)
        assertEquals("fakeip-dns", fakeServer.getString("tag"))
        assertEquals("fakeip", fakeServer.getString("type"))
        assertFalse(fakeServer.has("address"))
        assertEquals("198.18.0.0/15", fakeServer.getString("inet4_range"))
        assertEquals("fc00::/18", fakeServer.getString("inet6_range"))

        val remoteServer = servers.getJSONObject(1)
        assertEquals("remote-dns", remoteServer.getString("tag"))
        assertEquals("https", remoteServer.getString("type"))
        assertEquals("1.1.1.1", remoteServer.getString("server"))
        assertEquals("/dns-query", remoteServer.getString("path"))
        assertEquals("proxy", remoteServer.getString("detour"))
        assertFalse(remoteServer.has("address"))
    }

    @Test
    fun testMigrateStrategyInDnsServers() {
        val legacyConfig = """
            {
              "dns": {
                "servers": [
                  {
                    "tag": "remote-dns",
                    "address": "tcp://8.8.8.8:53",
                    "strategy": "prefer_ipv4",
                    "independent_cache": true
                  },
                  {
                    "tag": "local-dns",
                    "address": "local",
                    "strategy": "prefer_ipv4"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val dns = root.getJSONObject("dns")

        // Strategy should be promoted to root dns
        assertEquals("prefer_ipv4", dns.getString("strategy"))

        val servers = dns.getJSONArray("servers")
        assertEquals(2, servers.length())

        val remote = servers.getJSONObject(0)
        assertFalse(remote.has("strategy"))
        assertFalse(remote.has("independent_cache"))
        assertEquals("tcp", remote.getString("type"))
        assertEquals("8.8.8.8", remote.getString("server"))

        val local = servers.getJSONObject(1)
        assertFalse(local.has("strategy"))
        assertEquals("local", local.getString("type"))
    }

    @Test
    fun testMigrateFakeIpServerWithStrategy() {
        val legacyConfig = """
            {
              "dns": {
                "servers": [
                  {
                    "tag": "fakeip",
                    "address": "fakeip",
                    "strategy": "ipv4_only",
                    "detour": "direct"
                  }
                ],
                "fakeip": {
                  "enabled": true,
                  "inet4_range": "198.18.0.0/15"
                }
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val dns = root.getJSONObject("dns")

        assertFalse(dns.has("fakeip"))
        assertEquals("ipv4_only", dns.getString("strategy"))

        val servers = dns.getJSONArray("servers")
        assertEquals(1, servers.length())

        val fake = servers.getJSONObject(0)
        assertEquals("fakeip", fake.getString("type"))
        assertEquals("198.18.0.0/15", fake.getString("inet4_range"))
        assertFalse(fake.has("strategy"))
        assertFalse(fake.has("address"))
        assertFalse(fake.has("detour"))
    }

    @Test
    fun testMigrateLegacyFakeIpWithoutServer() {
        val legacyConfig = """
            {
              "dns": {
                "servers": [
                  {
                    "tag": "remote-dns",
                    "type": "https",
                    "server": "1.1.1.1"
                  }
                ],
                "fakeip": {
                  "enabled": true,
                  "inet4_range": "198.18.0.0/15"
                }
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val dns = root.getJSONObject("dns")

        assertFalse(dns.has("fakeip"))

        val servers = dns.getJSONArray("servers")
        assertEquals(2, servers.length())

        val fakeServer = servers.getJSONObject(1)
        assertEquals("fakeip", fakeServer.getString("tag"))
        assertEquals("fakeip", fakeServer.getString("type"))
        assertEquals("198.18.0.0/15", fakeServer.getString("inet4_range"))
    }

    @Test
    fun testMigrateLegacyDnsAddresses() {
        val legacyConfig = """
            {
              "dns": {
                "servers": [
                  {
                    "tag": "local-dns",
                    "address": "local"
                  },
                  {
                    "tag": "tcp-dns",
                    "address": "tcp://8.8.8.8:53"
                  },
                  {
                    "tag": "tls-dns",
                    "address": "tls://1.0.0.1:853"
                  },
                  {
                    "tag": "plain-dns",
                    "address": "9.9.9.9"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val servers = root.getJSONObject("dns").getJSONArray("servers")

        val local = servers.getJSONObject(0)
        assertEquals("local", local.getString("type"))
        assertFalse(local.has("address"))

        val tcp = servers.getJSONObject(1)
        assertEquals("tcp", tcp.getString("type"))
        assertEquals("8.8.8.8", tcp.getString("server"))
        assertEquals(53, tcp.getInt("server_port"))
        assertFalse(tcp.has("address"))

        val tls = servers.getJSONObject(2)
        assertEquals("tls", tls.getString("type"))
        assertEquals("1.0.0.1", tls.getString("server"))
        assertEquals(853, tls.getInt("server_port"))
        assertFalse(tls.has("address"))

        val plain = servers.getJSONObject(3)
        assertEquals("udp", plain.getString("type"))
        assertEquals("9.9.9.9", plain.getString("server"))
        assertFalse(plain.has("address"))
    }

    @Test
    fun testMigrateOutboundsAndRouteRulesStrategy() {
        val legacyConfig = """
            {
              "outbounds": [
                {
                  "type": "direct",
                  "tag": "direct",
                  "strategy": "prefer_ipv4"
                }
              ],
              "route": {
                "rules": [
                  {
                    "outbound": "direct",
                    "strategy": "prefer_ipv4"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        val outbounds = root.getJSONArray("outbounds")
        val out = outbounds.getJSONObject(0)
        assertFalse(out.has("strategy"))
        assertEquals("prefer_ipv4", out.getString("domain_strategy"))

        val rules = root.getJSONObject("route").getJSONArray("rules")
        val rule = rules.getJSONObject(0)
        assertFalse(rule.has("strategy"))
        assertEquals("prefer_ipv4", rule.getString("domain_strategy"))
    }

    @Test
    fun testMigrateExperimentalStoreRdrc() {
        val legacyConfig = """
            {
              "experimental": {
                "cache_file": {
                  "enabled": true,
                  "store_rdrc": true
                }
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val cacheFile = root.getJSONObject("experimental").getJSONObject("cache_file")

        assertFalse(cacheFile.has("store_rdrc"))
        assertTrue(cacheFile.getBoolean("store_dns"))
    }
}
