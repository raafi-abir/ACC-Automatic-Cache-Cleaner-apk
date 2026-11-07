package io.github.raafiabir.acc

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** True while any clean (manual or scheduled) is running. */
object RunState {
    val running = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/**
 * [freedBytes] and [remainingBytes] are -1 when usage access is missing and sizes couldn't be measured.
 * [appsTried] is only set in accessibility mode, where some apps can fail.
 */
data class CleanResult(
    val freedBytes: Long,
    val appsCleaned: Int,
    val remainingBytes: Long = -1L,
    val appsTried: Int = 0,
)

object Cleaner {
    private val PACKAGE_NAME = Regex("^[A-Za-z0-9_.]+$")

    // Accessibility mode takes several seconds per app, so by default skip small caches and cap the run.
    private const val A11Y_MIN_BYTES = 1L shl 20
    private const val A11Y_ALL_MIN_BYTES = 64L shl 10
    private const val A11Y_MAX_APPS = 40

    // After the main pass, apps still holding at least this much cache get a second, per-app pass.
    private const val SWEEP_MIN_BYTES = 256L shl 10

    private fun script(
        ctx: Context, backend: Backend, prefs: Prefs, pkgs: List<String>, excluded: Set<String>,
        cache: Boolean, junk: Boolean, leftover: Boolean, allowTrim: Boolean,
    ): String = buildString {
        appendLine("PKGS='${pkgs.joinToString(" ")}'")
        appendLine("EXCL=' ${excluded.joinToString(" ")} '")
        appendLine("DO_CACHE=${if (cache) 1 else 0}")
        appendLine("DO_JUNK=${if (junk) 1 else 0}")
        appendLine("DO_LEFT=${if (leftover) 1 else 0}")
        appendLine("ALLOW_TRIM=${if (allowTrim) 1 else 0}")
        appendLine("ROOT=${if (backend == Backend.Root) 1 else 0}")
        appendLine("SELF=${ctx.packageName}")
        append(ctx.assets.open("clean.sh").bufferedReader().use { it.readText() })
    }

    suspend fun run(ctx: Context, scheduled: Boolean = false): CleanResult {
        val prefs = Prefs(ctx)
        val scanner = CacheScanner(ctx)
        val backend = Executor.resolve(ctx) ?: throw IllegalStateException("No Shizuku, root or accessibility access")
        val excluded = prefs.excluded.filter { PACKAGE_NAME.matches(it) }.toSet()
        if (backend == Backend.Accessibility) return runAccessibility(ctx, prefs, scanner, excluded, scheduled)

        val hasAccess = scanner.hasUsageAccess()
        val before = if (hasAccess) scanner.scanCaches() else null

        // With sizes we only touch apps that actually have cache.
        val candidates = before?.filterValues { it > 0 }?.keys ?: scanner.installedPackages()
        val targets = candidates
            .filter { it != ctx.packageName && it !in excluded && PACKAGE_NAME.matches(it) }
            .sorted()

        val first = script(
            ctx, backend, prefs, targets, excluded,
            cache = prefs.cleanCache, junk = prefs.cleanJunk, leftover = prefs.cleanLeftover,
            allowTrim = excluded.isEmpty(),
        )
        val out = Executor.exec(ctx, backend, first)
        prefs.lastLog = out.takeLast(4_000)
        val ok = Regex("ok=(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IllegalStateException("The cleaning script didn't finish. " + out.trim().takeLast(160))
        val trimOk = Regex("trim=(\\d+|-)").find(out)?.groupValues?.get(1) == "0"
        val err = Regex("err=(.*)").find(out)?.groupValues?.get(1)?.trim()

        // Nothing worked for the cache step: say why instead of reporting a silent success.
        if (prefs.cleanCache && targets.isNotEmpty() && !trimOk && ok == 0) {
            throw IllegalStateException(
                if (excluded.isEmpty()) {
                    "Android refused to clear caches" + (if (!err.isNullOrBlank()) ": $err" else "")
                } else {
                    "This Android version won't clear single apps. Remove the excluded apps to use the global clean."
                }
            )
        }
        val cleaned = if (trimOk) (if (before != null) targets.size else 0) else ok

        delay(1_500) // let Android refresh its storage numbers before measuring again
        var after = if (hasAccess) scanner.scanCaches() else null

        // Second pass: Android's global trim can leave some apps untouched. Clear whatever still
        // holds cache, app by app. If the phone refuses single-app clearing this stops after three tries.
        if (prefs.cleanCache && after != null) {
            val leftovers = after
                .filter { (pkg, bytes) ->
                    bytes >= SWEEP_MIN_BYTES && pkg != ctx.packageName && pkg !in excluded && PACKAGE_NAME.matches(pkg)
                }
                .keys.sorted()
            if (leftovers.isNotEmpty()) {
                val sweep = script(
                    ctx, backend, prefs, leftovers, excluded,
                    cache = true, junk = false, leftover = false, allowTrim = false,
                )
                val out2 = Executor.exec(ctx, backend, sweep)
                prefs.lastLog = (prefs.lastLog + "\n--- second pass, ${leftovers.size} apps ---\n" + out2).takeLast(4_000)
                delay(1_500)
                after = scanner.scanCaches()
            }
        }

        return result(ctx, excluded, before, after, cleaned)
    }

    private fun result(
        ctx: Context, excluded: Set<String>, before: Map<String, Long>?, after: Map<String, Long>?,
        cleaned: Int, tried: Int = 0,
    ): CleanResult {
        val freed = if (before != null && after != null) {
            (before.values.sum() - after.values.sum()).coerceAtLeast(0L)
        } else -1L
        val remaining = after?.filterKeys { it != ctx.packageName && it !in excluded }?.values?.sum() ?: -1L
        return CleanResult(freed, cleaned, remaining, tried)
    }

    private suspend fun runAccessibility(
        ctx: Context, prefs: Prefs, scanner: CacheScanner, excluded: Set<String>, scheduled: Boolean,
    ): CleanResult {
        if (!prefs.cleanCache) throw IllegalStateException("App cache is off. This mode can only clear app cache.")
        val power = ctx.getSystemService(PowerManager::class.java)
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            throw IllegalStateException("The phone is locked or the screen is off")
        }
        // With WRITE_SECURE_SETTINGS the service is switched on just for this run.
        if (CacheAccessibilityService.instance == null) AccessibilityToggle.ensureEnabled(ctx)
        val svc = CacheAccessibilityService.instance
            ?: throw IllegalStateException("The accessibility service isn't running")

        try {
            // "Clean every app" is for manual runs only: scheduled runs live in a background job with a time limit.
            val everyApp = prefs.a11yAll && !scheduled
            val minBytes = if (everyApp) A11Y_ALL_MIN_BYTES else A11Y_MIN_BYTES

            val before = if (scanner.hasUsageAccess()) scanner.scanCaches() else null
            val candidates = if (before != null) {
                before.filterValues { it >= minBytes }.entries.sortedByDescending { it.value }.map { it.key }
            } else {
                scanner.userPackages()
            }
            val eligible = candidates.filter { it != ctx.packageName && it !in excluded }
            val targets = if (everyApp) eligible else eligible.take(A11Y_MAX_APPS)
            if (targets.isEmpty()) return CleanResult(if (before != null) 0L else -1L, 0, if (before != null) 0L else -1L)

            val cleaned = AccessibilityCleaner.run(ctx, svc, targets)

            delay(1_500)
            val after = if (before != null) scanner.scanCaches() else null
            return result(ctx, excluded, before, after, cleaned, tried = targets.size)
        } finally {
            // Banking apps such as bKash can refuse to run while an accessibility service is on.
            if (prefs.accessibilityOffAfter) svc.disableSelf()
        }
    }

    /** Runs a clean and stores the outcome so the UI can show it later. */
    suspend fun runRecorded(ctx: Context, scheduled: Boolean = false): kotlin.Result<CleanResult> {
        if (!RunState.running.compareAndSet(false, true)) {
            return kotlin.Result.failure(IllegalStateException("Already cleaning"))
        }
        try {
            return withContext(Dispatchers.Default) {
                val prefs = Prefs(ctx)
                try {
                    val r = run(ctx, scheduled)
                    prefs.saveLast(System.currentTimeMillis(), r.freedBytes, null, r.remainingBytes)
                    kotlin.Result.success(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    prefs.saveLast(System.currentTimeMillis(), -1L, e.message ?: "Cleaning failed")
                    kotlin.Result.failure(e)
                }
            }
        } finally {
            RunState.running.value = false
        }
    }
}
