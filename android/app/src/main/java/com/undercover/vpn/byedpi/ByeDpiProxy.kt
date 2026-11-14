package com.undercover.vpn.byedpi

/**
 * Thin JNI wrapper around the vendored ByeDPI core (app/src/main/cpp).
 *
 * [jniStartProxy] blocks for the whole lifetime of the proxy — it runs ByeDPI's
 * event loop on the calling thread — so it must never be invoked on the main
 * thread. Use [ByeDpiManager] instead of touching this class directly.
 */
internal class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    /** Blocks until the proxy stops. Returns ByeDPI's exit code. */
    external fun jniStartProxy(args: Array<String>): Int

    /** Shuts the listening socket down, which unblocks [jniStartProxy]. */
    external fun jniStopProxy(): Int

    /** Hard-closes the listening socket when a graceful shutdown did not take. */
    external fun jniForceClose(): Int
}
