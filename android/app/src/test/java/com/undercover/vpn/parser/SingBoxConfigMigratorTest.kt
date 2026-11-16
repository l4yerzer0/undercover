package com.undercover.vpn.parser

import com.undercover.vpn.shared.parser.SingBoxConfigMigrator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

        // 2. Servers must be migrated, and remote-dns must be first (sing-box prohibits fakeip as default server[0])
        val servers = dns.getJSONArray("servers")
        assertEquals(2, servers.length())

        val remoteServer = servers.getJSONObject(0)
        assertEquals("remote-dns", remoteServer.getString("tag"))
        assertEquals("https", remoteServer.getString("type"))
        assertEquals("1.1.1.1", remoteServer.getString("server"))
        assertEquals("/dns-query", remoteServer.getString("path"))
        assertEquals("proxy", remoteServer.getString("detour"))
        assertFalse(remoteServer.has("address"))

        val fakeServer = servers.getJSONObject(1)
        assertEquals("fakeip-dns", fakeServer.getString("tag"))
        assertEquals("fakeip", fakeServer.getString("type"))
        assertFalse(fakeServer.has("address"))
        assertEquals("198.18.0.0/15", fakeServer.getString("inet4_range"))
        assertEquals("fc00::/18", fakeServer.getString("inet6_range"))
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
    fun testMigrateFakeIpServerWithStrategyAndFallback() {
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
        // Since fakeip was the only server, a local fallback is prepended so server[0] is not fakeip
        assertEquals(2, servers.length())

        val fallback = servers.getJSONObject(0)
        assertEquals("dns-local", fallback.getString("tag"))
        assertEquals("local", fallback.getString("type"))

        val fake = servers.getJSONObject(1)
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

        val remoteServer = servers.getJSONObject(0)
        assertEquals("remote-dns", remoteServer.getString("tag"))

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

        // Strategy is promoted to root dns
        assertEquals("prefer_ipv4", root.getJSONObject("dns").getString("strategy"))

        val outbounds = root.getJSONArray("outbounds")
        val out = outbounds.getJSONObject(0)
        assertFalse(out.has("strategy"))
        assertFalse(out.has("domain_strategy"))

        val rules = root.getJSONObject("route").getJSONArray("rules")
        val rule = rules.getJSONObject(0)
        assertFalse(rule.has("strategy"))
        assertFalse(rule.has("domain_strategy"))
    }

    @Test
    fun testMigrateInboundSniffAndTunAddresses() {
        val legacyConfig = """
            {
              "inbounds": [
                {
                  "type": "tun",
                  "tag": "tun-in",
                  "inet4_address": "172.19.0.1/30",
                  "inet6_address": "fdfe:dcba:9876::1/126",
                  "inet4_route_address": ["0.0.0.0/1", "128.0.0.0/1"],
                  "inet6_route_address": ["::/1"],
                  "sniff": true,
                  "sniff_override_destination": true,
                  "sniff_timeout": "300ms",
                  "domain_strategy": "prefer_ipv4",
                  "sniffing": { "enabled": true }
                }
              ],
              "route": {
                "rules": [
                  {
                    "outbound": "direct"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        // Legacy sniffing options must be removed
        assertFalse(inbound.has("sniff"))
        assertFalse(inbound.has("sniff_override_destination"))
        assertFalse(inbound.has("sniff_timeout"))
        assertFalse(inbound.has("domain_strategy"))
        assertFalse(inbound.has("sniffing"))

        // Legacy addresses merged
        assertFalse(inbound.has("inet4_address"))
        assertFalse(inbound.has("inet6_address"))
        assertTrue(inbound.has("address"))
        val addrArr = inbound.getJSONArray("address")
        assertEquals(2, addrArr.length())
        assertEquals("172.19.0.1/30", addrArr.getString(0))
        assertEquals("fdfe:dcba:9876::1/126", addrArr.getString(1))

        // Route address merged
        assertFalse(inbound.has("inet4_route_address"))
        assertFalse(inbound.has("inet6_route_address"))
        val routeAddrArr = inbound.getJSONArray("route_address")
        assertEquals(3, routeAddrArr.length())

        // Sniff rule added to route.rules
        val rules = root.getJSONObject("route").getJSONArray("rules")
        val firstRule = rules.getJSONObject(0)
        assertEquals("sniff", firstRule.getString("action"))
        assertEquals("300ms", firstRule.getString("timeout"))
    }

    @Test
    fun testMigrateSpecialOutboundsBlockAndDns() {
        val legacyConfig = """
            {
              "outbounds": [
                {
                  "type": "block",
                  "tag": "block-out"
                },
                {
                  "type": "dns",
                  "tag": "dns-out"
                },
                {
                  "type": "direct",
                  "tag": "direct",
                  "override_address": "1.1.1.1",
                  "override_port": 443
                }
              ],
              "route": {
                "rules": [
                  {
                    "protocol": "dns",
                    "outbound": "dns-out"
                  },
                  {
                    "domain_suffix": "ads.com",
                    "outbound": "block-out"
                  },
                  {
                    "type": "field",
                    "outbound": "direct"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        val outbounds = root.getJSONArray("outbounds")
        // block and dns outbounds removed, only direct remains
        assertEquals(1, outbounds.length())
        val direct = outbounds.getJSONObject(0)
        assertEquals("direct", direct.getString("tag"))
        assertFalse(direct.has("override_address"))
        assertFalse(direct.has("override_port"))

        val rules = root.getJSONObject("route").getJSONArray("rules")
        assertEquals(3, rules.length())

        // Rule 0: dns outbound -> action hijack-dns
        val dnsRule = rules.getJSONObject(0)
        assertFalse(dnsRule.has("outbound"))
        assertEquals("hijack-dns", dnsRule.getString("action"))

        // Rule 1: block outbound -> action reject
        val blockRule = rules.getJSONObject(1)
        assertFalse(blockRule.has("outbound"))
        assertEquals("reject", blockRule.getString("action"))

        // Rule 2: type=field stripped
        val directRule = rules.getJSONObject(2)
        assertFalse(directRule.has("type"))
        assertEquals("direct", directRule.getString("outbound"))
    }

    @Test
    fun testMigrateLegacyWireGuardOutboundToEndpoint() {
        val legacyConfig = """
            {
              "outbounds": [
                {
                  "type": "wireguard",
                  "tag": "wg-out",
                  "server": "1.2.3.4",
                  "server_port": 51820,
                  "local_address": ["10.0.0.2/32"],
                  "private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                  "peer_public_key": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
                  "mtu": 1420
                },
                {
                  "type": "direct",
                  "tag": "direct"
                }
              ]
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        // wireguard outbound migrated to endpoints
        val outbounds = root.getJSONArray("outbounds")
        assertEquals(1, outbounds.length())
        assertEquals("direct", outbounds.getJSONObject(0).getString("tag"))

        val endpoints = root.getJSONArray("endpoints")
        assertEquals(1, endpoints.length())
        val ep = endpoints.getJSONObject(0)
        assertEquals("wireguard", ep.getString("type"))
        assertEquals("wg-out", ep.getString("tag"))
        assertEquals(1420, ep.getInt("mtu"))

        val peers = ep.getJSONArray("peers")
        assertEquals(1, peers.length())
        val peer = peers.getJSONObject(0)
        assertEquals("1.2.3.4", peer.getString("address"))
        assertEquals(51820, peer.getInt("port"))
        assertEquals("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", peer.getString("public_key"))
        assertTrue(peer.getJSONArray("allowed_ips").length() > 0)
    }

    @Test
    fun testMigrateDnsRulesOutboundAny() {
        val legacyConfig = """
            {
              "dns": {
                "servers": [
                  {
                    "tag": "local-dns",
                    "type": "local"
                  }
                ],
                "rules": [
                  {
                    "outbound": "any",
                    "server": "local-dns"
                  },
                  {
                    "domain": "google.com",
                    "outbound": "any",
                    "server": "local-dns"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        // route.default_domain_resolver set
        assertEquals("local-dns", root.getJSONObject("route").getString("default_domain_resolver"))

        val dnsRules = root.getJSONObject("dns").getJSONArray("rules")
        // Rule that only had outbound: any & server is removed
        assertEquals(1, dnsRules.length())
        val rule = dnsRules.getJSONObject(0)
        assertEquals("google.com", rule.getString("domain"))
        assertFalse(rule.has("outbound"))
    }

    @Test
    fun testMigrateLegacyGeoIpAndGeositeInRules() {
        val legacyConfig = """
            {
              "route": {
                "rules": [
                  {
                    "geoip": "private",
                    "outbound": "direct"
                  },
                  {
                    "geosite": "cn",
                    "outbound": "direct"
                  },
                  {
                    "source_geoip": "cn",
                    "outbound": "block"
                  }
                ]
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val rules = root.getJSONObject("route").getJSONArray("rules")

        val rule0 = rules.getJSONObject(0)
        assertFalse(rule0.has("geoip"))
        assertTrue(rule0.getBoolean("ip_is_private"))

        val rule1 = rules.getJSONObject(1)
        assertFalse(rule1.has("geosite"))
        assertEquals("geosite-cn", rule1.getString("rule_set"))

        val rule2 = rules.getJSONObject(2)
        assertFalse(rule2.has("source_geoip"))
        assertEquals("geoip-cn", rule2.getString("rule_set"))
        assertTrue(rule2.getBoolean("rule_set_ipcidr_match_source"))
        assertEquals("reject", rule2.getString("action"))
    }

    @Test
    fun testMigrateClashApiCacheFile() {
        val legacyConfig = """
            {
              "experimental": {
                "clash_api": {
                  "external_controller": "127.0.0.1:9090",
                  "cache_file": "cache.db",
                  "cache_id": "profile1"
                }
              }
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)
        val exp = root.getJSONObject("experimental")
        val clashApi = exp.getJSONObject("clash_api")
        val cacheFile = exp.getJSONObject("cache_file")

        assertFalse(clashApi.has("cache_file"))
        assertFalse(clashApi.has("cache_id"))
        assertTrue(cacheFile.getBoolean("enabled"))
        assertEquals("cache.db", cacheFile.getString("path"))
        assertEquals("profile1", cacheFile.getString("cache_id"))
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

    @Test
    fun testMigrateAddressStrategyAndResolver() {
        val legacyConfig = """
            {
              "dns": {
                "address_strategy": "prefer_ipv4",
                "servers": [
                  {
                    "tag": "remote-dns",
                    "type": "https",
                    "server": "1.1.1.1",
                    "address_strategy": "prefer_ipv4",
                    "address_resolver": "local-dns",
                    "network": "udp",
                    "protocol": "dns",
                    "port": 443
                  },
                  {
                    "tag": "local-dns",
                    "type": "local"
                  }
                ]
              },
              "outbounds": [
                {
                  "type": "socks",
                  "tag": "socks-out",
                  "host": "1.2.3.4",
                  "port": 1080,
                  "address_strategy": "prefer_ipv4",
                  "address_resolver": "local-dns"
                }
              ]
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        val dns = root.getJSONObject("dns")
        assertFalse(dns.has("address_strategy"))
        assertEquals("prefer_ipv4", dns.getString("strategy"))

        val servers = dns.getJSONArray("servers")
        val remote = servers.getJSONObject(0)
        assertFalse(remote.has("address_strategy"))
        assertFalse(remote.has("address_resolver"))
        assertFalse(remote.has("network"))
        assertFalse(remote.has("protocol"))
        assertFalse(remote.has("port"))
        assertEquals(443, remote.getInt("server_port"))

        val outbounds = root.getJSONArray("outbounds")
        val socks = outbounds.getJSONObject(0)
        assertFalse(socks.has("address_strategy"))
        assertFalse(socks.has("address_resolver"))
        assertEquals("local-dns", socks.getString("domain_resolver"))
        assertFalse(socks.has("host"))
        assertFalse(socks.has("port"))
        assertEquals("1.2.3.4", socks.getString("server"))
        assertEquals(1080, socks.getInt("server_port"))
    }

    @Test
    fun testMigrateRemnawaveSubscriptionConfig() {
        val legacyConfig = """
            {
              "dns": {
                "fakeip": {
                  "enabled": true,
                  "inet4_range": "198.18.0.0/15"
                },
                "independent_cache": true,
                "servers": [
                  {
                    "tag": "remote",
                    "address": "fakeip"
                  },
                  {
                    "tag": "dns-remote",
                    "address": "https://1.1.1.1/dns-query",
                    "address_strategy": "prefer_ipv4",
                    "strategy": "ipv4_only",
                    "address_resolver": "dns-direct"
                  },
                  {
                    "tag": "dns-direct",
                    "address": "local"
                  }
                ],
                "rules": [
                  {
                    "outbound": "any",
                    "server": "dns-direct"
                  }
                ]
              },
              "inbounds": [
                {
                  "type": "tun",
                  "tag": "tun-in",
                  "sniff": true,
                  "inet4_address": "172.19.0.1/30"
                }
              ],
              "outbounds": [
                {
                  "type": "selector",
                  "tag": "⚡ VelvetVPN",
                  "outbounds": ["vless-node"]
                },
                {
                  "type": "vless",
                  "tag": "vless-node",
                  "server": "example.com",
                  "server_port": 443,
                  "uuid": "00000000-0000-0000-0000-000000000000"
                },
                {
                  "type": "block",
                  "tag": "block"
                }
              ]
            }
        """.trimIndent()

        val migrated = SingBoxConfigMigrator.migrate(legacyConfig)
        val root = JSONObject(migrated)

        val dns = root.getJSONObject("dns")
        assertFalse(dns.has("fakeip"))
        assertFalse(dns.has("independent_cache"))
        assertFalse(dns.has("address_strategy"))

        val servers = dns.getJSONArray("servers")
        // FakeIP should not be server[0]
        assertNotEquals("fakeip", servers.getJSONObject(0).optString("type"))

        for (i in 0 until servers.length()) {
            val s = servers.getJSONObject(i)
            assertFalse(s.has("address"))
            assertFalse(s.has("strategy"))
            assertFalse(s.has("domain_strategy"))
            assertFalse(s.has("address_strategy"))
            assertFalse(s.has("address_resolver"))
            assertFalse(s.has("independent_cache"))
        }

        val inbounds = root.getJSONArray("inbounds")
        for (i in 0 until inbounds.length()) {
            val ib = inbounds.getJSONObject(i)
            assertFalse(ib.has("sniff"))
            assertFalse(ib.has("inet4_address"))
            assertFalse(ib.has("inet6_address"))
        }

        val outbounds = root.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.getJSONObject(i)
            assertNotEquals("block", ob.optString("type"))
            assertNotEquals("dns", ob.optString("type"))
        }
    }
}
