package io.github.raafiabir.acc

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class CleaningStopped(message: String) : Exception(message)

/**
 * Clears cache the way you would by hand: App info > Storage > Clear cache, once per app.
 * It only reads or taps windows of the Settings app, and only ever taps a button whose label
 * is exactly "Clear cache" (taken from Settings' own strings, so it matches your language).
 */
object AccessibilityCleaner {
    val progress = MutableStateFlow<String?>(null)

    private class Labels(val storage: List<String>, val clearCache: List<String>, val deny: List<String>)
    private enum class Hit { Clicked, Disabled }

    suspend fun run(ctx: Context, svc: AccessibilityService, packages: List<String>): Int =
        withContext(Dispatchers.Main) {
            val settingsPkg = settingsPackage(ctx) ?: throw IllegalStateException("Couldn't find the Settings app")
            val labels = loadLabels(ctx, settingsPkg)
            val pm = ctx.packageManager
            var cleaned = 0
            var stopped = false
            try {
                packages.forEachIndexed { i, pkg ->
                    val name = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
                    progress.value = "Cleaning ${i + 1} of ${packages.size}: $name"
                    if (i > 0 && !stillOnSettings(svc, settingsPkg, ctx.packageName)) {
                        stopped = true
                        throw CleaningStopped("Stopped because you left the Settings screen")
                    }
                    if (cleanOne(svc, settingsPkg, labels, pkg, name)) cleaned++
                }
            } finally {
                progress.value = null
                // Hand the screen back to ACC, unless the user walked away on purpose.
                if (!stopped) {
                    svc.startActivity(
                        Intent(svc, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                }
            }
            cleaned
        }

    private suspend fun cleanOne(
        svc: AccessibilityService, settingsPkg: String, labels: Labels, pkg: String, appName: String,
    ): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            svc.startActivity(intent)
        } catch (e: Exception) {
            return false
        }
        // Wait until the page shows this app, so we don't tap on the previous app's screen.
        if (appName.isNotBlank()) {
            poll(svc, settingsPkg, 2_500, scroll = false) { root ->
                if (root.findAccessibilityNodeInfosByText(appName).isNotEmpty()) true else null
            }
        }
        poll(svc, settingsPkg, 5_000) { tryClick(it, labels.storage, labels.deny)?.takeIf { h -> h == Hit.Clicked } }
            ?: return false
        delay(300)
        val hit = poll(svc, settingsPkg, 5_000) { tryClick(it, labels.clearCache, labels.deny) } ?: return false
        delay(400)
        return hit == Hit.Clicked
    }

    /** Calls [step] on the Settings window every 250 ms until it returns non-null or time runs out. */
    private suspend fun <T : Any> poll(
        svc: AccessibilityService,
        settingsPkg: String,
        timeoutMs: Long,
        scroll: Boolean = true,
        step: (AccessibilityNodeInfo) -> T?,
    ): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var tries = 0
        var scrolls = 0
        while (SystemClock.uptimeMillis() < end) {
            val root = svc.rootInActiveWindow
            if (root != null && root.packageName == settingsPkg) {
                step(root)?.let { return it }
                if (scroll && ++tries >= 3 && scrolls < 3 && scrollForward(root)) scrolls++
            }
            delay(250)
        }
        return null
    }

    private fun tryClick(root: AccessibilityNodeInfo, labels: List<String>, deny: List<String>): Hit? {
        for (label in labels) {
            for (node in root.findAccessibilityNodeInfosByText(label)) {
                val text = node.text?.toString()?.trim() ?: continue
                if (!text.equals(label, ignoreCase = true)) continue          // exact match only
                if (deny.any { text.equals(it, ignoreCase = true) }) continue // never "Clear data/storage"
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target == null) continue
                if (!target.isEnabled) return Hit.Disabled // nothing to clear
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return Hit.Clicked
            }
        }
        return null
    }

    private fun scrollForward(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun stillOnSettings(svc: AccessibilityService, settingsPkg: String, self: String): Boolean {
        val pkg = svc.rootInActiveWindow?.packageName?.toString() ?: return true // null while transitioning
        return pkg == settingsPkg || pkg == self
    }

    private fun settingsPackage(ctx: Context): String? {
        val probe = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        return ctx.packageManager.resolveActivity(probe, 0)?.activityInfo?.packageName
    }

    /** Button labels come from Settings' own resources so they match the device language. */
    private fun loadLabels(ctx: Context, settingsPkg: String): Labels {
        val res = runCatching { ctx.packageManager.getResourcesForApplication(settingsPkg) }.getOrNull()
        fun str(vararg names: String): List<String> = names.mapNotNull { name ->
            val id = res?.getIdentifier(name, "string", settingsPkg) ?: 0
            if (id == 0) null else runCatching { res!!.getString(id).trim() }.getOrNull()
        }
        return Labels(
            storage = (str("storage_settings_for_app", "storage_settings", "storage_label") +
                listOf("Storage & cache", "Storage")).distinct(),
            clearCache = (str("clear_cache_btn_text") + "Clear cache").distinct(),
            deny = (str("clear_user_data_text", "clear_data_dlg_title") +
                listOf("Clear storage", "Clear data")).distinct(),
        )
    }
}
