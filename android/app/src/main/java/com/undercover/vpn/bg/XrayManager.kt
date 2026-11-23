package com.undercover.vpn.bg

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.undercover.vpn.shared.parser.SubscriptionNodesHelper
import com.undercover.vpn.shared.parser.XrayConfigGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
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

            val preparedJson = prepareAndroidConfig(context, xrayJson)
            val configFile = File(context.cacheDir, "xray-active.json")
            configFile.writeText(preparedJson)

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
                        lines.forEach { line -> Log.i(TAG, line) }
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

    /**
     * Enhances an Xray JSON config for Android where /etc/resolv.conf does not exist:
     *  - Pre-resolves proxy server domains via Android's native netd resolver into dns.hosts
     *  - Injects active system DNS servers + Yandex/Google/Cloudflare fallbacks
     *  - Routes Xray's own DNS queries ("dns-out") to the "direct" freedom outbound
     *  - Forces streamSettings.sockopt.domainStrategy = "UseIPv4" so Xray uses its internal
     *    DNS/hosts table instead of Go's net.Dialer (/etc/resolv.conf).
     */
    private fun prepareAndroidConfig(context: Context, xrayJson: String): String {
        return try {
            val root = JSONObject(xrayJson)
            val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
            dns.put("tag", "dns-out")
            dns.put("queryStrategy", "UseIPv4")

            val hostsObj = dns.optJSONObject("hosts") ?: JSONObject().also { dns.put("hosts", it) }
            val outbounds = root.optJSONArray("outbounds")
            if (outbounds != null) {
                for (i in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(i) ?: continue
                    val protocol = ob.optString("protocol").lowercase()
                    if (protocol == "freedom" || protocol == "blackhole") continue

                    // Force internal DNS resolution via sockopt.domainStrategy
                    val stream = ob.optJSONObject("streamSettings") ?: JSONObject().also { ob.put("streamSettings", it) }
                    val sockopt = stream.optJSONObject("sockopt") ?: JSONObject().also { stream.put("sockopt", it) }
                    if (!sockopt.has("domainStrategy")) {
                        sockopt.put("domainStrategy", "UseIPv4")
                    }

                    // Collect server hostnames and pre-resolve them via Android native DNS
                    val settings = ob.optJSONObject("settings") ?: continue
                    val hostList = mutableListOf<String>()
                    settings.optJSONArray("vnext")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            arr.optJSONObject(j)?.optString("address")?.takeIf { it.isNotBlank() }?.let { hostList.add(it) }
                        }
                    }
                    settings.optJSONArray("servers")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            arr.optJSONObject(j)?.optString("address")?.takeIf { it.isNotBlank() }?.let { hostList.add(it) }
                        }
                    }
                    for (host in hostList.distinct()) {
                        if (isIpLiteral(host)) continue
                        try {
                            val addrs = InetAddress.getAllByName(host)
                            val ipv4 = addrs.firstOrNull { it is Inet4Address }?.hostAddress
                            val resolved = ipv4 ?: addrs.firstOrNull()?.hostAddress
                            if (!resolved.isNullOrBlank()) {
                                hostsObj.put(host, resolved)
                                Log.i(TAG, "Pre-resolved xray server $host -> $resolved")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to pre-resolve xray server $host", e)
                        }
                    }
                }
            }

            // Build DNS servers list: Android active network DNS first, then public fallbacks
            val dnsServers = LinkedHashSet<String>()
            getActiveNetworkDnsServers(context).forEach { dnsServers.add(it) }
            dnsServers.add("77.88.8.8")
            dnsServers.add("8.8.8.8")
            dnsServers.add("1.1.1.1")
            val serversArray = JSONArray()
            dnsServers.forEach { serversArray.put(it) }
            dns.put("servers", serversArray)

            // Ensure dns-out queries go through direct outbound instead of looping into proxy
            val routing = root.optJSONObject("routing") ?: JSONObject().also { root.put("routing", it) }
            val rules = routing.optJSONArray("rules") ?: JSONArray().also { routing.put("rules", it) }
            var hasDnsOutRule = false
            for (i in 0 until rules.length()) {
                val r = rules.optJSONObject(i) ?: continue
                val inTags = r.optJSONArray("inboundTag")
                if (inTags != null) {
                    for (j in 0 until inTags.length()) {
                        if (inTags.optString(j) == "dns-out") {
                            hasDnsOutRule = true
                            break
                        }
                    }
                }
            }
            if (!hasDnsOutRule) {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", JSONArray().put("dns-out"))
                    put("outboundTag", "direct")
                })
            }

            root.toString(2)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enhance xray config for Android, using raw config", e)
            xrayJson
        }
    }

    private fun getActiveNetworkDnsServers(context: Context): List<String> {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
            val linkProps = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
            val servers = linkProps.dnsServers
            val ipv4 = servers.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
            ipv4.filter { it.isNotBlank() && !it.startsWith("127.") && !it.startsWith("172.19.") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isIpLiteral(host: String): Boolean {
        val clean = host.trim().removeSurrounding("[", "]")
        if (clean.contains(':')) return true // IPv6
        val parts = clean.split('.')
        return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
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
