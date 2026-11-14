package com.undercover.vpn.byedpi

/**
 * Bridge that lets the native ByeDPI core reach `VpnService.protect()`.
 *
 * [protectSocket] is called from C for every socket ByeDPI opens (see the local
 * patch in cpp/byedpi/proxy.c). It is resolved by JNI under this exact name and
 * signature, so both this class and [ByeDpiProxy] are kept in proguard-rules.pro.
 *
 * When no VPN service is running — proxy-only mode — there is nothing to protect
 * against and the call reports false, which ByeDPI treats as non-fatal.
 */
object ByeDpiSocketProtector {
    @Volatile
    private var protector: ((Int) -> Boolean)? = null

    fun register(protect: (Int) -> Boolean) {
        protector = protect
    }

    fun unregister() {
        protector = null
    }

    @JvmStatic
    fun protectSocket(fd: Int): Boolean = protector?.invoke(fd) ?: false
}
