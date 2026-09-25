package com.rewardshield

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Checks the device for tooling that ad-skippers depend on. Every check can be bypassed
 * by a determined attacker, so combine them with server-side verification and use the
 * score to decide how much to trust a reward, not as a hard gate.
 */
class EnvironmentChecks(private val context: Context) {

    fun assess(): Assessment = Assessment(
        listOfNotNull(
            accessibilityAutomation(),
            hookFrameworks(),
            fridaServer(),
            debugger(),
            root(),
            emulator(),
        )
    )

    /** Auto-clickers and "skip ad" tools usually run as accessibility services. */
    private fun accessibilityAutomation(): Signal? {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return null
        val suspicious = enabled.split(':')
            .filter { it.isNotBlank() }
            .filterNot { svc -> TRUSTED_ACCESSIBILITY.any { svc.startsWith(it) } }
        return if (suspicious.isEmpty()) null
        else Signal("accessibility_service", 20, "Enabled: ${suspicious.joinToString()}")
    }

    /** Frida, Xposed/LSPosed and Substrate leave libraries in our own memory map. */
    private fun hookFrameworks(): Signal? {
        val maps = runCatching { File("/proc/self/maps").readText() }.getOrNull() ?: return null
        val hit = HOOK_MARKERS.firstOrNull { maps.contains(it, ignoreCase = true) }
            ?: runCatching { Class.forName("de.robv.android.xposed.XposedBridge"); "XposedBridge" }.getOrNull()
        return hit?.let { Signal("hook_framework", 70, "Found $it in process") }
    }

    /** Must not be called on the main thread (network I/O). */
    private fun fridaServer(): Signal? = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
        Signal("frida_port", 60, "Something listens on Frida's default port 27042")
    }.getOrNull()

    private fun debugger(): Signal? =
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger())
            Signal("debugger", 40, "Debugger attached") else null

    private fun root(): Signal? {
        val found = ROOT_PATHS.firstOrNull { File(it).exists() }
        val testKeys = Build.TAGS?.contains("test-keys") == true
        return when {
            found != null -> Signal("root", 25, "Found $found")
            testKeys -> Signal("root", 15, "Build signed with test-keys")
            else -> null
        }
    }

    private fun emulator(): Signal? {
        val fp = Build.FINGERPRINT.lowercase()
        val isEmu = fp.startsWith("generic") || fp.contains("emulator") ||
            Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk")
        return if (isEmu) Signal("emulator", 15, "Emulator build: ${Build.FINGERPRINT}") else null
    }

    companion object {
        private val HOOK_MARKERS = listOf("frida", "gum-js-loop", "xposed", "lsposed", "substrate", "libriru")
        private val ROOT_PATHS = listOf(
            "/system/xbin/su", "/system/bin/su", "/sbin/su", "/data/adb/magisk", "/data/adb/ksu",
        )
        private val TRUSTED_ACCESSIBILITY = listOf(
            "com.google.android.marvin.talkback",
            "com.samsung.android.accessibility",
            "com.google.android.accessibility",
        )
    }
}
