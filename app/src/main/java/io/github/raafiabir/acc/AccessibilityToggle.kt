package io.github.raafiabir.acc

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.delay

/**
 * Switches ACC's own accessibility service on for a clean, so it can be off the rest of the time.
 * Needs WRITE_SECURE_SETTINGS, which can only be granted once from a computer (adb), or through Shizuku or root.
 */
object AccessibilityToggle {

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun me(ctx: Context) = ComponentName(ctx, CacheAccessibilityService::class.java)

    private fun entries(ctx: Context): List<String> =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.filter { it.isNotBlank() } ?: emptyList()

    private fun isListed(ctx: Context): Boolean =
        entries(ctx).any { ComponentName.unflattenFromString(it) == me(ctx) }

    /** Returns true if the service is running afterwards. */
    suspend fun ensureEnabled(ctx: Context, timeoutMs: Long = 8_000): Boolean {
        if (CacheAccessibilityService.instance != null) return true
        if (!canWriteSecure(ctx)) return false
        try {
            if (!isListed(ctx)) {
                val list = entries(ctx) + me(ctx).flattenToString()
                Settings.Secure.putString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":")
                )
            }
            Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (e: SecurityException) {
            return false
        }
        var waited = 0L
        while (CacheAccessibilityService.instance == null && waited < timeoutMs) {
            delay(250)
            waited += 250
        }
        return CacheAccessibilityService.instance != null
    }
}
