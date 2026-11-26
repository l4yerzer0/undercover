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

    @Volatile
    var activeAuth: XrayConfigGenerator.XrayBridgeAuth? = null
        private set

    val currentPort: Int
        get() = activeAuth?.basePort ?: XrayConfigGenerator.LOCAL_SOCKS_PORT

    val currentAuth: XrayConfigGenerator.XrayBridgeAuth?
        get() = activeAuth

    private var authenticatorInstalled = false

    fun ensureAuthenticator() {
        if (authenticatorInstalled) return
        authenticatorInstalled = true
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                val auth = activeAuth ?: return null
                if (requestingPort == auth.basePort) {
                    return java.net.PasswordAuthentication(auth.username, auth.password.toCharArray())
                }
                return null
            }
        })
    }

    fun allocatePort(): Int {
        var port = 0
        for (attempt in 0..10) {
            try {
                java.net.ServerSocket(0).use { socket ->
                    port = socket.localPort
                }
                if (port in 1024..65530) break
            } catch (_: Exception) {
            }
        }
        return if (port > 0) port else 20808
    }

    fun prepareSession(): XrayConfigGenerator.XrayBridgeAuth {
        val port = allocatePort()
        val bytes = ByteArray(24)
        java.security.SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val user = "uc_" + hex.take(8)
        val pass = hex.substring(8)
        val auth = XrayConfigGenerator.XrayBridgeAuth(basePort = port, username = user, password = pass)
        activeAuth = auth
        ensureAuthenticator()
        return auth
    }

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

    fun ensureForDualConfig(
        context: Context,
        primaryConfigPath: String?,
        primaryNodeTag: String?,
        secondaryConfigPath: String?,
        secondaryNodeTag: String?,
        dualEnabled: Boolean,
    ) {
        val list = mutableListOf<Pair<Int, Pair<String?, String?>>>()
        list.add(0 to (primaryConfigPath to primaryNodeTag))
        if (dualEnabled) {
            list.add(1 to (secondaryConfigPath to secondaryNodeTag))
        }
        ensureForMultiConfig(context, list)
    }

    fun ensureForMultiConfig(
        context: Context,
        endpoints: List<Pair<Int, Pair<String?, String?>>>,
        auth: XrayConfigGenerator.XrayBridgeAuth? = activeAuth,
    ) {
        val indexedXray = mutableListOf<Pair<Int, String>>()
        for ((slotIndex, pathAndTag) in endpoints) {
            val (configPath, nodeTag) = pathAndTag
            if (configPath == null) continue
            val map = SubscriptionNodesHelper.loadXrayMap(configPath)
            val xray = if (nodeTag != null) map[nodeTag] ?: map["proxy"] else map.values.firstOrNull()
            if (!xray.isNullOrBlank()) {
                indexedXray.add(slotIndex to xray)
            }
        }
        if (indexedXray.isEmpty()) {
            stop()
            return
        }
        val effectiveAuth = auth ?: activeAuth
        val combinedXray = XrayConfigGenerator.combineMultiXrayConfigs(
            indexedConfigs = indexedXray,
            basePort = effectiveAuth?.basePort ?: XrayConfigGenerator.LOCAL_SOCKS_PORT,
            auth = effectiveAuth,
        )
        if (combinedXray == null) {
            stop()
            return
        }
        activeAuth = effectiveAuth
        startWithJson(context, combinedXray)
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
        val text = activeConfigText ?: ""
        val portsToCheck = mutableListOf<Int>()
        try {
            val inbounds = org.json.JSONObject(text).optJSONArray("inbounds")
            if (inbounds != null) {
                for (i in 0 until inbounds.length()) {
                    val p = inbounds.optJSONObject(i)?.optInt("port", 0) ?: 0
                    if (p > 0) portsToCheck.add(p)
                }
            }
        } catch (_: Exception) {
        }
        if (portsToCheck.isEmpty()) portsToCheck.add(XrayConfigGenerator.LOCAL_SOCKS_PORT)

        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                throw IllegalStateException("xray-core exited prematurely with code ${proc.exitValue()}")
            }
            var allReady = true
            for (port in portsToCheck) {
                try {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(XrayConfigGenerator.LOCAL_SOCKS_HOST, port),
                            100,
                        )
                    }
                } catch (_: Exception) {
                    allReady = false
                    break
                }
            }
            if (allReady) {
                Log.i(TAG, "xray-core SOCKS5 bridge ready on ports $portsToCheck")
                return
            }
            Thread.sleep(50)
        }
        Log.w(TAG, "Timed out waiting for xray-core SOCKS5 port(s), proceeding anyway")
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
        activeAuth = null
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
