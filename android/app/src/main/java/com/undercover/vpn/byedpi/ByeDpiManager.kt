package com.undercover.vpn.byedpi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Owns the lifetime of the embedded ByeDPI SOCKS5 proxy.
 *
 * ByeDPI listens on loopback only; sing-box reaches it through a `socks`
 * outbound. ByeDPI's own outgoing sockets leave the device directly rather than
 * re-entering the tunnel — see docs/per-app-bypass.md, section on the routing
 * loop — which requires the VPN service to exclude this app's own package.
 */
object ByeDpiManager {
    private const val TAG = "ByeDpiManager"
    private const val LISTEN_ADDRESS = "127.0.0.1"
    private const val STARTUP_TIMEOUT_MS = 3_000L
    private const val SHUTDOWN_TIMEOUT_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var proxy: ByeDpiProxy? = null
    private var job: Job? = null

    /** Loopback port ByeDPI is listening on, or null when it is not running. */
    @Volatile
    var port: Int? = null
        private set

    val isRunning: Boolean get() = port != null

    /**
     * Starts ByeDPI with [strategy] (a ByeDPI command line, e.g. `-s3 -Ar`) and
     * returns the loopback port it listens on. Suspends until the listener
     * accepts connections, so the caller can hand the port to sing-box right
     * away. Returns null if ByeDPI did not come up in time.
     */
    suspend fun start(strategy: String): Int? = mutex.withLock {
        if (port != null) {
            Log.w(TAG, "already running on port $port")
            return@withLock port
        }

        val listenPort = allocatePort()
        val args = buildArgs(listenPort, strategy)
        Log.i(TAG, "starting: ${args.joinToString(" ")}")

        val instance = ByeDpiProxy()
        proxy = instance
        job = scope.launch {
            val code = runCatching { instance.jniStartProxy(args) }
                .getOrElse {
                    Log.e(TAG, "byedpi crashed", it)
                    -1
                }
            Log.i(TAG, "byedpi exited with code $code")
            port = null
        }

        val up = withTimeoutOrNull(STARTUP_TIMEOUT_MS) {
            while (!canConnect(listenPort)) {
                delay(50)
            }
            true
        }

        if (up != true) {
            Log.e(TAG, "byedpi did not start listening on $listenPort")
            stopLocked()
            return@withLock null
        }

        port = listenPort
        Log.i(TAG, "byedpi is up on $LISTEN_ADDRESS:$listenPort")
        listenPort
    }

    suspend fun stop() = mutex.withLock { stopLocked() }

    private suspend fun stopLocked() {
        val instance = proxy ?: return
        Log.i(TAG, "stopping byedpi")

        runCatching { instance.jniStopProxy() }
            .onFailure { Log.e(TAG, "jniStopProxy failed", it) }

        val stopped = withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { job?.join(); true }
        if (stopped != true) {
            Log.w(TAG, "graceful shutdown timed out, forcing socket close")
            runCatching { instance.jniForceClose() }
                .onFailure { Log.e(TAG, "jniForceClose failed", it) }
            job?.cancel()
        }

        proxy = null
        job = null
        port = null
    }

    /**
     * ByeDPI takes a listen port on the command line rather than picking one,
     * so bind an ephemeral port, note it, and release it. The window between
     * release and ByeDPI's own bind is a race, but a lost race surfaces as a
     * startup timeout rather than as silent breakage.
     */
    private fun allocatePort(): Int = ServerSocket(0).use { it.localPort }

    private fun buildArgs(listenPort: Int, strategy: String): Array<String> {
        val head = listOf("ciadpi", "-i", LISTEN_ADDRESS, "-p", listenPort.toString())
        val tail = strategy.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return (head + tail).toTypedArray()
    }

    private fun canConnect(listenPort: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LISTEN_ADDRESS, listenPort), 200)
        }
        true
    }.getOrDefault(false)
}
