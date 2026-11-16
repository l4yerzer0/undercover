package com.undercover.vpn.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingManager {
    suspend fun ping(host: String, port: Int, timeoutMs: Int = 2000): Int? = withContext(Dispatchers.IO) {
        if (host.isBlank() || port <= 0) return@withContext null
        try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            val elapsed = (System.currentTimeMillis() - start).toInt()
            elapsed
        } catch (_: Exception) {
            null
        }
    }
}
