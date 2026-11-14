package com.undercover.vpn.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import com.undercover.vpn.xposed.HookErrorStore

abstract class SafeMethodHook(private val source: String) : XC_MethodHook() {
    @Volatile
    private var disabled = false

    final override fun beforeHookedMethod(param: MethodHookParam) {
        if (disabled) return
        try {
            beforeHook(param)
        } catch (e: Throwable) {
            disabled = true
            HookErrorStore.e(source, "Hook disabled due to unrecoverable error", e)
        }
    }

    final override fun afterHookedMethod(param: MethodHookParam) {
        if (disabled) return
        try {
            afterHook(param)
        } catch (e: Throwable) {
            disabled = true
            HookErrorStore.e(source, "Hook disabled due to unrecoverable error", e)
        }
    }

    protected open fun beforeHook(param: MethodHookParam) {}
    protected open fun afterHook(param: MethodHookParam) {}
}
