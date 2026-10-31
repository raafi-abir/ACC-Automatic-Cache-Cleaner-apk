package io.github.raafiabir.acc

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [freedBytes] is -1 when usage access is missing and the size couldn't be measured. */
data class CleanResult(val freedBytes: Long, val appsCleaned: Int)

object Cleaner {
    private val PACKAGE_NAME = Regex("^[A-Za-z0-9_.]+$")

    // Accessibility mode takes several seconds per app, so skip small caches and cap the run.
    private const val A11Y_MIN_BYTES = 1L shl 20
    private const val A11Y_MAX_APPS = 40

    suspend fun run(ctx: Context): CleanResult {
        val prefs = Prefs(ctx)
        val scanner = CacheScanner(ctx)
        val backend = Executor.resolve(ctx) ?: throw IllegalStateException("No Shizuku, root or accessibility access")
        val excluded = prefs.excluded.filter { PACKAGE_NAME.matches(it) }.toSet()
        if (backend == Backend.Accessibility) return runAccessibility(ctx, prefs, scanner, excluded)

        val hasAccess = scanner.hasUsageAccess()
        val before = if (hasAccess) scanner.scanCaches() else null

        // With sizes we only touch apps that actually have cache.
        val candidates = before?.filterValues { it > 0 }?.keys ?: scanner.installedPackages()
        val targets = candidates
            .filter { it != ctx.packageName && it !in excluded && PACKAGE_NAME.matches(it) }
            .sorted()

        val script = buildString {
            appendLine("PKGS='${targets.joinToString(" ")}'")
            appendLine("EXCL=' ${excluded.joinToString(" ")} '")
            appendLine("DO_CACHE=${if (prefs.cleanCache) 1 else 0}")
            appendLine("DO_JUNK=${if (prefs.cleanJunk) 1 else 0}")
            appendLine("DO_LEFT=${if (prefs.cleanLeftover) 1 else 0}")
            appendLine("ALLOW_TRIM=${if (excluded.isEmpty()) 1 else 0}")
            appendLine("ROOT=${if (backend == Backend.Root) 1 else 0}")
            appendLine("SELF=${ctx.packageName}")
            append(ctx.assets.open("clean.sh").bufferedReader().use { it.readText() })
        }

        val out = Executor.exec(ctx, backend, script)
        val cleaned = Regex("ok=(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IllegalStateException("Cleaning script didn't finish")

        val after = if (hasAccess) scanner.scanCaches() else null
        val freed = if (before != null && after != null) {
            (before.values.sum() - after.values.sum()).coerceAtLeast(0L)
        } else -1L

        return CleanResult(freed, cleaned)
    }

    private suspend fun runAccessibility(
        ctx: Context, prefs: Prefs, scanner: CacheScanner, excluded: Set<String>,
    ): CleanResult {
        if (!prefs.cleanCache) throw IllegalStateException("App cache is off. This mode can only clear app cache.")
        val svc = CacheAccessibilityService.instance
            ?: throw IllegalStateException("The accessibility service isn't running")
        val power = ctx.getSystemService(PowerManager::class.java)
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            throw IllegalStateException("The phone is locked or the screen is off")
        }

        val before = if (scanner.hasUsageAccess()) scanner.scanCaches() else null
        val candidates = if (before != null) {
            before.filterValues { it >= A11Y_MIN_BYTES }.entries.sortedByDescending { it.value }.map { it.key }
        } else {
            scanner.userPackages()
        }
        val targets = candidates.filter { it != ctx.packageName && it !in excluded }.take(A11Y_MAX_APPS)
        if (targets.isEmpty()) return CleanResult(if (before != null) 0L else -1L, 0)

        val cleaned = AccessibilityCleaner.run(ctx, svc, targets)

        val after = if (before != null) scanner.scanCaches() else null
        val freed = if (before != null && after != null) {
            (before.values.sum() - after.values.sum()).coerceAtLeast(0L)
        } else -1L
        return CleanResult(freed, cleaned)
    }

    /** Runs a clean and stores the outcome so the UI can show it later. */
    suspend fun runRecorded(ctx: Context): kotlin.Result<CleanResult> = withContext(Dispatchers.Default) {
        val prefs = Prefs(ctx)
        try {
            val r = run(ctx)
            prefs.saveLast(System.currentTimeMillis(), r.freedBytes, null)
            kotlin.Result.success(r)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.saveLast(System.currentTimeMillis(), -1L, e.message ?: "Cleaning failed")
            kotlin.Result.failure(e)
        }
    }
}
