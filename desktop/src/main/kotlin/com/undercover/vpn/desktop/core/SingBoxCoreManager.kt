package com.undercover.vpn.desktop.core

import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.DesktopSettings
import com.undercover.vpn.desktop.model.ProxyMode
import com.undercover.vpn.desktop.model.VpnState
import com.undercover.vpn.shared.parser.SingBoxConfigMigrator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

object SingBoxCoreManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val vpnState = _vpnState.asStateFlow()

    private val _logsFlow = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 500)
    val logsFlow = _logsFlow.asSharedFlow()

    private val recentLogs = mutableListOf<String>()
    fun getRecentLogs(): List<String> = synchronized(recentLogs) { recentLogs.toList() }

    private var currentProcess: Process? = null
    private val isStopping = AtomicBoolean(false)

    init {
        // Restore system proxy on JVM exit
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                currentProcess?.destroyForcibly()
                runBlocking { SystemProxyController.disableSystemProxy() }
            } catch (_: Exception) {}
        })
    }

    /**
     * Resolves the sing-box binary automatically:
     * 1. Check local installed path (~/.local/share/undercover/sing-box)
     * 2. Automatically extract from embedded classpath resources (/bin/<os>-<arch>/...)
     * 3. Check system PATH
     * 4. Silently auto-download as a fallback
     */
    fun ensureSingBoxBinary(customPath: String? = null): File {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.canExecute()) return f
        }

        val home = System.getProperty("user.home")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val exeName = if (isWindows) "sing-box.exe" else "sing-box"
        val targetDir = File(home, ".local/share/undercover").also { it.mkdirs() }
        val localBin = File(targetDir, exeName)

        // 1. If already on disk and executable, return it
        if (localBin.exists() && localBin.canExecute() && localBin.length() > 1000) {
            return localBin
        }

        // 2. Extract embedded binary from resources
        val osName = System.getProperty("os.name").lowercase()
        val archName = System.getProperty("os.arch").lowercase()

        val osPart = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "darwin"
            else -> "linux"
        }
        val archPart = when {
            archName.contains("aarch64") || archName.contains("arm64") -> "arm64"
            else -> "amd64"
        }

        val resourcePath = "/bin/$osPart-$archPart/$exeName"
        val resourceStream = SingBoxCoreManager::class.java.getResourceAsStream(resourcePath)
        if (resourceStream != null) {
            try {
                log("[CORE] Extracting embedded sing-box ($osPart-$archPart)...")
                localBin.outputStream().use { out ->
                    resourceStream.copyTo(out)
                }
                localBin.setExecutable(true, false)
                log("[CORE] sing-box core ready at ${localBin.absolutePath}")
                return localBin
            } catch (e: Exception) {
                log("[CORE] Failed extracting embedded binary: ${e.message}")
            }
        }

        // 3. Check system PATH
        val pathEnv = System.getenv("PATH") ?: ""
        val separator = if (isWindows) ";" else ":"
        for (dir in pathEnv.split(separator)) {
            val candidate = File(dir, exeName)
            if (candidate.exists() && candidate.canExecute()) {
                return candidate
            }
        }

        // 4. Silent download fallback
        log("[CORE] Downloading sing-box core in background...")
        return runBlocking { downloadSingBox(targetBin = localBin, goos = osPart, goarch = archPart) }
    }

    private suspend fun downloadSingBox(targetBin: File, goos: String, goarch: String): File = withContext(Dispatchers.IO) {
        val version = "1.11.1"
        val ext = if (goos == "windows") "zip" else "tar.gz"
        val archiveName = "sing-box-$version-$goos-$goarch"
        val downloadUrl = "https://github.com/SagerNet/sing-box/releases/download/v$version/$archiveName.$ext"

        val tempArchive = File.createTempFile("singbox_download", ".$ext")
        try {
            URL(downloadUrl).openStream().use { input ->
                Files.copy(input, tempArchive.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            if (goos == "windows") {
                val zipFile = java.util.zip.ZipFile(tempArchive)
                val entry = zipFile.entries().asSequence().firstOrNull { it.name.endsWith("sing-box.exe") }
                    ?: throw IllegalStateException("sing-box.exe not found in archive")
                zipFile.getInputStream(entry).use { inStream ->
                    Files.copy(inStream, targetBin.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                val pb = ProcessBuilder("tar", "-xzf", tempArchive.absolutePath, "-C", "/tmp")
                pb.start().waitFor()
                val extracted = File("/tmp").walkTopDown().firstOrNull { it.isFile && it.name == "sing-box" }
                    ?: throw IllegalStateException("sing-box binary not found after tar extraction")
                Files.copy(extracted.toPath(), targetBin.toPath(), StandardCopyOption.REPLACE_EXISTING)
                targetBin.setExecutable(true, false)
            }

            log("[CORE] sing-box installed successfully: ${targetBin.absolutePath}")
            targetBin
        } finally {
            tempArchive.delete()
        }
    }

    suspend fun start(profile: DesktopProfile, settings: DesktopSettings) = withContext(Dispatchers.IO) {
        if (_vpnState.value is VpnState.Connected || _vpnState.value is VpnState.Connecting) {
            stop()
        }

        _vpnState.value = VpnState.Connecting(profile.name)
        log("[APP] Connecting to profile '${profile.name}'...")

        val binary = try {
            ensureSingBoxBinary(settings.customSingBoxPath)
        } catch (e: Exception) {
            val err = "Failed to initialize sing-box: ${e.message}"
            log("[ERROR] $err")
            _vpnState.value = VpnState.Error(err)
            return@withContext
        }

        try {
            // Build runnable configuration
            val runnableConfig = buildRunnableConfig(profile, settings)
            StorageManager.runningConfigFile.writeText(runnableConfig)
            log("[CONFIG] Prepared configuration (${StorageManager.runningConfigFile.length()} bytes)")

            // Test config first
            val testProcess = ProcessBuilder(binary.absolutePath, "check", "-c", StorageManager.runningConfigFile.absolutePath).start()
            val checkOutput = testProcess.inputStream.bufferedReader().readText() + testProcess.errorStream.bufferedReader().readText()
            val checkCode = testProcess.waitFor()
            if (checkCode != 0) {
                val err = "Configuration check failed: $checkOutput"
                log("[ERROR] $err")
                _vpnState.value = VpnState.Error(err)
                return@withContext
            }

            // Start sing-box
            isStopping.set(false)
            val process = ProcessBuilder(binary.absolutePath, "run", "-c", StorageManager.runningConfigFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            currentProcess = process

            // Pipe output logs
            scope.launch {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { log(it) }
                }
            }

            // Watch process exit
            scope.launch {
                val exitCode = process.waitFor()
                if (!isStopping.get()) {
                    log("[PROCESS] sing-box process exited unexpectedly with code $exitCode")
                    SystemProxyController.disableSystemProxy()
                    _vpnState.value = VpnState.Error("Process exited unexpectedly (code $exitCode)")
                }
            }

            delay(400)

            // If system proxy mode, enable system proxy
            if (settings.proxyMode == ProxyMode.SYSTEM_PROXY) {
                log("[PROXY] Enabling system proxy (SOCKS 127.0.0.1:${settings.socksPort}, HTTP 127.0.0.1:${settings.httpPort})...")
                SystemProxyController.enableSystemProxy(settings.socksPort, settings.httpPort)
            }

            val activeNode = profile.selectedOutboundTag ?: profile.name
            _vpnState.value = VpnState.Connected(
                profileName = profile.name,
                nodeName = activeNode,
                connectedSince = System.currentTimeMillis(),
                localSocksPort = settings.socksPort,
                localHttpPort = settings.httpPort,
                mode = settings.proxyMode
            )
            log("[APP] Cover established via $activeNode")

        } catch (e: Exception) {
            val err = "Connection error: ${e.message}"
            log("[ERROR] $err")
            stop()
            _vpnState.value = VpnState.Error(err)
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        if (_vpnState.value is VpnState.Disconnected) return@withContext

        _vpnState.value = VpnState.Disconnecting
        log("[APP] Dropping cover...")
        isStopping.set(true)

        // Disable system proxy first
        SystemProxyController.disableSystemProxy()

        // Stop sing-box process
        currentProcess?.let { proc ->
            try {
                proc.destroy()
                withTimeoutOrNull(2000) {
                    while (proc.isAlive) delay(50)
                }
                if (proc.isAlive) proc.destroyForcibly()
            } catch (_: Exception) {}
        }
        currentProcess = null

        _vpnState.value = VpnState.Disconnected
        log("[APP] Disconnected. System network settings restored.")
    }

    private fun buildRunnableConfig(profile: DesktopProfile, settings: DesktopSettings): String {
        val migrated = SingBoxConfigMigrator.migrate(profile.migratedConfig.ifEmpty { profile.rawConfig })
        val root = JSONObject(migrated)

        // Construct desktop inbounds based on selected proxy mode
        val inbounds = JSONArray()
        if (settings.proxyMode == ProxyMode.SYSTEM_PROXY) {
            val mixed = JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "127.0.0.1")
                put("listen_port", settings.socksPort)
                put("sniff", true)
            }
            val http = JSONObject().apply {
                put("type", "http")
                put("tag", "http-in")
                put("listen", "127.0.0.1")
                put("listen_port", settings.httpPort)
                put("sniff", true)
            }
            inbounds.put(mixed)
            inbounds.put(http)
        } else {
            val tun = JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "undercover0")
                put("address", JSONArray().apply { put("172.19.0.1/30") })
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "system")
                put("sniff", true)
            }
            inbounds.put(tun)
        }
        root.put("inbounds", inbounds)

        // Ensure log level is info for visibility
        val logObj = root.optJSONObject("log") ?: JSONObject()
        logObj.put("level", "info")
        logObj.put("timestamp", true)
        root.put("log", logObj)

        return root.toString(2)
    }

    private fun log(message: String) {
        val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val entry = "[$timestamp] $message"
        synchronized(recentLogs) {
            if (recentLogs.size > 500) recentLogs.removeAt(0)
            recentLogs.add(entry)
        }
        _logsFlow.tryEmit(entry)
    }
}
