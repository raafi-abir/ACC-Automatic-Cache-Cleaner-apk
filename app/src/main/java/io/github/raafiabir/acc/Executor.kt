package io.github.raafiabir.acc

import android.content.Context

enum class Backend(val label: String) {
    Auto("Auto"), Shizuku("Shizuku"), Root("Root"), Accessibility("Accessibility");

    fun next() = entries[(ordinal + 1) % entries.size]
}

/** Picks Shizuku, root or accessibility according to the user's choice and runs scripts on it. */
object Executor {

    /** The backend that would run right now, or null if none is usable. */
    suspend fun resolve(ctx: Context): Backend? {
        val shizukuReady = ShizukuBridge.state(ctx) == ShizukuState.Ready
        val accessibilityOn = CacheAccessibilityService.instance != null
        return when (Prefs(ctx).backend) {
            Backend.Shizuku -> if (shizukuReady) Backend.Shizuku else null
            Backend.Root -> if (RootBridge.available()) Backend.Root else null
            Backend.Accessibility -> if (accessibilityOn) Backend.Accessibility else null
            Backend.Auto -> when {
                shizukuReady -> Backend.Shizuku
                RootBridge.available() -> Backend.Root
                accessibilityOn -> Backend.Accessibility
                else -> null
            }
        }
    }

    suspend fun exec(ctx: Context, backend: Backend, script: String): String = when (backend) {
        Backend.Root -> RootBridge.exec(script)
        Backend.Accessibility -> throw IllegalStateException("The accessibility backend can't run shell scripts")
        else -> ShizukuBridge.exec(ctx, script)
    }
}
