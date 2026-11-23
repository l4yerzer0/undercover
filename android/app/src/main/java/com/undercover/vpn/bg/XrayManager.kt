package com.undercover.vpn.bg

import android.content.Context
import android.os.Build
import android.util.Log
import com.undercover.vpn.shared.parser.SubscriptionNodesHelper
import com.undercover.vpn.shared.parser.XrayConfigGenerator
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.ZipFile

object XrayManager {
    private const val TAG = "XrayManager"
    private val lock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var activeConfigText: String? = null

    val isRunning: Boolean
        get() = process?.isAlive == true

    fun getVersion(context: Context): String {
        return try {
            val binary = resolveXrayBinary(context)
            val proc = ProcessBuilder(binary.absolutePath, "version").start()
            val line = proc.inputStream.bufferedReader().readLine().orEmpty()
            proc.waitFor()
            line.ifEmpty { "Xray-core v25.1.30" }
        } catch (_: Exception) {
            "Xray-core v25.1.30"
        }
    }

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

            val binary = resolveXrayBinary(context)
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

    private fun resolveXrayBinary(context: Context): File {
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (nativeLib.exists() && nativeLib.length() > 0L) {
            return nativeLib
        }

        // Fallback: extract directly from APK if the installer didn't unpack native libs
        val extracted = File(context.filesDir, "bin/libxray.so").apply { parentFile?.mkdirs() }
        if (extracted.exists() && extracted.length() > 0L) {
            return extracted
        }

        val apkSources = buildList {
            context.applicationInfo.sourceDir?.let { add(it) }
            context.applicationInfo.splitSourceDirs?.forEach { add(it) }
        }
        val abis = Build.SUPPORTED_ABIS.toList()
        for (apkPath in apkSources) {
            try {
                ZipFile(apkPath).use { zip ->
                    for (abi in abis) {
                        val entry = zip.getEntry("lib/$abi/libxray.so") ?: continue
                        zip.getInputStream(entry).use { input ->
                            extracted.outputStream().use { output -> input.copyTo(output) }
                        }
                        extracted.setExecutable(true, false)
                        Log.i(TAG, "Extracted libxray.so ($abi) from $apkPath")
                        return extracted
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inspect APK $apkPath for libxray.so", e)
            }
        }

        throw IllegalStateException("Xray core binary not found: ${nativeLib.absolutePath}")
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
