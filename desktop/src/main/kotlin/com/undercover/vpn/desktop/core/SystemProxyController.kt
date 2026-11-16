package com.undercover.vpn.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SystemProxyController {
    private val osName = System.getProperty("os.name").lowercase()

    suspend fun enableSystemProxy(socksPort: Int, httpPort: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                osName.contains("linux") -> {
                    // GNOME / standard freedesktop settings
                    runCommand("gsettings", "set", "org.gnome.system.proxy", "mode", "manual")
                    runCommand("gsettings", "set", "org.gnome.system.proxy.socks", "host", "127.0.0.1")
                    runCommand("gsettings", "set", "org.gnome.system.proxy.socks", "port", socksPort.toString())
                    runCommand("gsettings", "set", "org.gnome.system.proxy.http", "host", "127.0.0.1")
                    runCommand("gsettings", "set", "org.gnome.system.proxy.http", "port", httpPort.toString())
                    runCommand("gsettings", "set", "org.gnome.system.proxy.https", "host", "127.0.0.1")
                    runCommand("gsettings", "set", "org.gnome.system.proxy.https", "port", httpPort.toString())
                    true
                }
                osName.contains("win") -> {
                    val proxyServer = "socks=127.0.0.1:$socksPort;http=127.0.0.1:$httpPort;https=127.0.0.1:$httpPort"
                    runCommand("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f")
                    runCommand("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "/v", "ProxyServer", "/t", "REG_SZ", "/d", proxyServer, "/f")
                    true
                }
                osName.contains("mac") -> {
                    // Try Wi-Fi and Ethernet
                    listOf("Wi-Fi", "Ethernet").forEach { iface ->
                        runCommand("networksetup", "-setsocksfirewallproxy", iface, "127.0.0.1", socksPort.toString())
                        runCommand("networksetup", "-setsocksfirewallproxystate", iface, "on")
                        runCommand("networksetup", "-setwebproxy", iface, "127.0.0.1", httpPort.toString())
                        runCommand("networksetup", "-setwebproxystate", iface, "on")
                        runCommand("networksetup", "-setsecurewebproxy", iface, "127.0.0.1", httpPort.toString())
                        runCommand("networksetup", "-setsecurewebproxystate", iface, "on")
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun disableSystemProxy(): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                osName.contains("linux") -> {
                    runCommand("gsettings", "set", "org.gnome.system.proxy", "mode", "none")
                    true
                }
                osName.contains("win") -> {
                    runCommand("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f")
                    true
                }
                osName.contains("mac") -> {
                    listOf("Wi-Fi", "Ethernet").forEach { iface ->
                        runCommand("networksetup", "-setsocksfirewallproxystate", iface, "off")
                        runCommand("networksetup", "-setwebproxystate", iface, "off")
                        runCommand("networksetup", "-setsecurewebproxystate", iface, "off")
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun runCommand(vararg args: String): Int {
        return try {
            val process = ProcessBuilder(*args).start()
            process.waitFor()
        } catch (_: Exception) {
            -1
        }
    }
}
