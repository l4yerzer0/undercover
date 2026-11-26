package com.undercover.vpn.manager

import com.undercover.vpn.shared.manager.models.LocalServer
import com.undercover.vpn.shared.manager.store.LocalServerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalServerStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testStoreLifecycle() {
        val testDir = tempFolder.newFolder("undercover_test")

        // Initial load on empty dir
        val initialStore = LocalServerStore.loadStore(testDir)
        assertEquals(0, initialStore.servers.size)

        // Upsert first server
        val server1 = LocalServer(
            host = "95.217.1.1",
            sshPort = 22,
            user = "root",
            alias = "My VPS",
            protocol = "vless-reality",
            vpnPort = 443,
            serverName = "gateway.icloud.com",
            publicKey = "pubkey123"
        )
        LocalServerStore.upsertServer(testDir, server1)

        val loadedAfterFirst = LocalServerStore.loadStore(testDir)
        assertEquals(1, loadedAfterFirst.servers.size)
        assertEquals("95.217.1.1", loadedAfterFirst.servers[0].host)
        assertEquals("My VPS", loadedAfterFirst.servers[0].alias)

        // Upsert second server
        val server2 = LocalServer(
            host = "1.2.3.4",
            sshPort = 2222,
            user = "ubuntu",
            alias = "Backup VPS",
            protocol = "hysteria2",
            vpnPort = 8443,
            serverName = "backup.example.com"
        )
        LocalServerStore.upsertServer(testDir, server2)

        val loadedAfterSecond = LocalServerStore.loadStore(testDir)
        assertEquals(2, loadedAfterSecond.servers.size)

        // Update server 1 alias
        val updatedServer1 = server1.copy(alias = "Primary VPS")
        LocalServerStore.upsertServer(testDir, updatedServer1)

        val loadedAfterUpdate = LocalServerStore.loadStore(testDir)
        assertEquals(2, loadedAfterUpdate.servers.size)
        val found = loadedAfterUpdate.servers.find { it.host == "95.217.1.1" }
        assertNotNull(found)
        assertEquals("Primary VPS", found?.alias)

        // Remove server 2
        LocalServerStore.removeServer(testDir, "1.2.3.4")
        val loadedAfterDelete = LocalServerStore.loadStore(testDir)
        assertEquals(1, loadedAfterDelete.servers.size)
        assertEquals("95.217.1.1", loadedAfterDelete.servers[0].host)
    }
}
