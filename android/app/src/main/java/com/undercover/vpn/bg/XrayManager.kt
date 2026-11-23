package com.undercover.vpn.bg

import android.content.Context
import android.util.Log
import com.undercover.vpn.shared.parser.SubscriptionNodesHelper
import com.undercover.vpn.shared.parser.XrayConfigGenerator
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object XrayManager {
    private const val TAG = "XrayManager"
    private val lock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var activeConfigText: String? = null

    fun ensureForConfig(context: Context, singBoxConfigPath: String) {
        val xrayJson = SubscriptionNodesHelper.resolveActiveXrayConfig(singBoxConfigPath)
        if (xrayJson == null) {
            stop()
            return
        }
        startWithJson(context, xrayJson)
    }

    fun startWithJson(context: Context, xrayJson: String) {
        synchronized(lock) {
            val current = process
            if (current != null && current.isAlive && activeConfigText == xrayJson) {
                return
            }
            stopLocked()

            val binary = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
            if (!binary.exists()) {
                throw IllegalStateException("Xray core binary not found: ${binary.absolutePath}")
            }
            if (!binary.canExecute()) {
                binary.setExecutable(true)
            }

            val configFile = File(context.cacheDir, "xray-active.json")
            configFile.writeText(xrayJson)

            Log.i(TAG, "Starting xray-core from ${binary.absolutePath}")
            val proc = ProcessBuilder(binary.absolutePath, "run", "-c", configFile.absolutePath)
                .directory(context.cacheDir)
                .redirectErrorStream(true)
                .start()

            process = proc
            activeConfigText = xrayJson

            Thread({
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> Log.d(TAG, line) }
                    }
                } catch (_: Exception) {
                }
            }, "xray-log-drain").apply {
                isDaemon = true
                start()
            }

            waitForSocksReady(proc)
        }
    }

    private fun waitForSocksReady(proc: Process) {
        val deadline = System.currentTimeMillis() + 3000L
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                throw IllegalStateException("xray-core exited prematurely with code ${proc.exitValue()}")
            }
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(XrayConfigGenerator.LOCAL_SOCKS_HOST, XrayConfigGenerator.LOCAL_SOCKS_PORT),
                        100,
                    )
                }
                Log.i(TAG, "xray-core SOCKS5 bridge ready on ${XrayConfigGenerator.LOCAL_SOCKS_HOST}:${XrayConfigGenerator.LOCAL_SOCKS_PORT}")
                return
            } catch (_: Exception) {
                Thread.sleep(50)
            }
        }
        Log.w(TAG, "Timed out waiting for xray-core SOCKS5 port, proceeding anyway")
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        val proc = process ?: return
        process = null
        activeConfigText = null
        try {
            proc.destroy()
            if (proc.isAlive) {
                proc.destroyForcibly()
            }
            Log.i(TAG, "Stopped xray-core")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop xray-core", e)
        }
    }
}
