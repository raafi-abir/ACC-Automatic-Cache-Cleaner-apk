package io.github.raafiabir.acc

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val system: Boolean)

class CacheScanner(private val ctx: Context) {

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun installedPackages(): List<String> =
        ctx.packageManager.getInstalledApplications(0).map { it.packageName }

    /** Non-system apps. Used when cache sizes are unavailable. */
    fun userPackages(): List<String> =
        ctx.packageManager.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { it.packageName }

    /** Package name to cache bytes. Only meaningful when [hasUsageAccess] is true. */
    suspend fun scanCaches(): Map<String, Long> = withContext(Dispatchers.IO) {
        val stats = ctx.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        installedPackages().associateWith { pkg ->
            runCatching { stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, user).cacheBytes }
                .getOrDefault(0L)
        }
    }

    suspend fun installedApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .map {
                AppEntry(
                    pkg = it.packageName,
                    label = it.loadLabel(pm).toString(),
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
