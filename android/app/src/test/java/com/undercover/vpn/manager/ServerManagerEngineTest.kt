package com.undercover.vpn.manager

import com.undercover.vpn.shared.manager.engine.ServerManagerEngine
import com.undercover.vpn.shared.manager.models.ManagerProtocols
import com.undercover.vpn.shared.manager.models.RemoteState
import com.undercover.vpn.shared.manager.models.SingBoxInboundConfig
import com.undercover.vpn.shared.manager.models.SingBoxInboundUser
import com.undercover.vpn.shared.manager.models.SingBoxServerConfig
import com.undercover.vpn.shared.manager.models.StateUser
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerManagerEngineTest {

    @Test
    fun testGeneratePassword() {
        val pwd = ServerManagerEngine.generatePassword()
        assertNotNull(pwd)
        assertEquals(32, pwd.length) // 16 bytes hex = 32 chars
        assertTrue(pwd.all { it in "0123456789abcdef" })
    }

    @Test
    fun testGenerateUuid() {
        val uuid = ServerManagerEngine.generateUuid()
        assertNotNull(uuid)
        assertEquals(36, uuid.length)
        assertTrue(uuid.contains("-"))
    }

    @Test
    fun testRemoteStateSerialization() {
        val jsonStr = """
            {
              "version": "1.0.0",
              "sing_box_version": "1.11.4",
              "protocol": "vless-reality",
              "port": 443,
              "server_name": "gateway.icloud.com",
              "public_key": "some-public-key",
              "short_id": "abcd1234",
              "created_at": "2026-11-26T12:00:00Z",
              "users": [
                {
                  "name": "root-user",
                  "uuid": "e0b029fb-b6f7-4dc4-b7c4-54c7b827361a",
                  "created_at": "2026-11-26T12:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val parsed = ServerManagerEngine.json.decodeFromString<RemoteState>(jsonStr)
        assertEquals("1.0.0", parsed.version)
        assertEquals("1.11.4", parsed.singBoxVersion)
        assertEquals(ManagerProtocols.VLESS_REALITY, parsed.protocol)
        assertEquals(443, parsed.port)
        assertEquals("gateway.icloud.com", parsed.serverName)
        assertEquals("some-public-key", parsed.publicKey)
        assertEquals("abcd1234", parsed.shortId)
        assertEquals(1, parsed.users.size)
        assertEquals("root-user", parsed.users[0].name)
        assertEquals("e0b029fb-b6f7-4dc4-b7c4-54c7b827361a", parsed.users[0].uuid)

        // Reserialize
        val encoded = ServerManagerEngine.json.encodeToString(parsed)
        val roundtrip = ServerManagerEngine.json.decodeFromString<RemoteState>(encoded)
        assertEquals(parsed, roundtrip)
    }

    @Test
    fun testSingBoxServerConfigSerialization() {
        val configStr = """
            {
              "log": {
                "disabled": false,
                "level": "warn",
                "timestamp": true
              },
              "inbounds": [
                {
                  "type": "vless",
                  "tag": "vless-in",
                  "listen": "::",
                  "listen_port": 443,
                  "users": [
                    {
                      "name": "alice",
                      "uuid": "11111111-2222-3333-4444-555555555555",
                      "flow": "xtls-rprx-vision"
                    }
                  ],
                  "tls": {
                    "enabled": true,
                    "server_name": "gateway.icloud.com",
                    "reality": {
                      "enabled": true,
                      "handshake": {
                        "server": "gateway.icloud.com",
                        "server_port": 443
                      },
                      "private_key": "private-key-data",
                      "short_id": ["abcd1234"]
                    }
                  }
                }
              ],
              "outbounds": [
                {
                  "type": "direct",
                  "tag": "direct"
                }
              ]
            }
        """.trimIndent()

        val cfg = ServerManagerEngine.json.decodeFromString<SingBoxServerConfig>(configStr)
        assertEquals(1, cfg.inbounds.size)
        assertEquals("vless", cfg.inbounds[0].type)
        assertEquals(443, cfg.inbounds[0].listenPort)
        assertEquals(1, cfg.inbounds[0].users.size)
        assertEquals("alice", cfg.inbounds[0].users[0].name)
        assertEquals(true, cfg.inbounds[0].tls?.reality?.enabled)
        assertEquals(listOf("abcd1234"), cfg.inbounds[0].tls?.reality?.shortId)

        // Add a user
        val newUser = SingBoxInboundUser(
            name = "bob",
            uuid = "22222222-3333-4444-5555-666666666666",
            flow = "xtls-rprx-vision"
        )
        val updatedCfg = cfg.copy(
            inbounds = listOf(
                cfg.inbounds[0].copy(
                    users = cfg.inbounds[0].users + newUser
                )
            )
        )
        assertEquals(2, updatedCfg.inbounds[0].users.size)
        assertEquals("bob", updatedCfg.inbounds[0].users[1].name)

        // Remove a user
        val remainingUsers = updatedCfg.inbounds[0].users.filterNot { it.name == "alice" }
        val prunedCfg = updatedCfg.copy(
            inbounds = listOf(
                updatedCfg.inbounds[0].copy(users = remainingUsers)
            )
        )
        assertEquals(1, prunedCfg.inbounds[0].users.size)
        assertEquals("bob", prunedCfg.inbounds[0].users[0].name)
    }
}
