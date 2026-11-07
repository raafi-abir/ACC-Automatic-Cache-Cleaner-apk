package io.github.raafiabir.acc

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val pageUrl: String?, val apkUrl: String?, val notes: String)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** Asks GitHub for the latest release of this app's repo. Nothing else is sent or read. */
object UpdateChecker {
    private const val GITHUB = "https://github.com/"

    suspend fun check(ctx: Context): UpdateResult = withContext(Dispatchers.IO) {
        val result = fetch()
        Prefs(ctx).saveUpdateResult(result)
        result
    }

    private fun fetch(): UpdateResult = try {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ACC-Shizuku/${BuildConfig.VERSION_NAME}")
            when (val code = conn.responseCode) {
                200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> UpdateResult.UpToDate // no release published yet
                403, 429 -> UpdateResult.Failed("GitHub is limiting requests right now. Try again later.")
                else -> UpdateResult.Failed("GitHub answered with error $code")
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        UpdateResult.Failed("Couldn't reach GitHub")
    } catch (e: JSONException) {
        UpdateResult.Failed("Unexpected answer from GitHub")
    }

    private fun parse(body: String): UpdateResult {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        if (tag.isEmpty() || !Versions.isNewer(tag, BuildConfig.VERSION_NAME)) return UpdateResult.UpToDate

        var apk: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apk = asset.optString("browser_download_url")
                    break
                }
            }
        }
        // Only ever hand out github.com links.
        return UpdateResult.Available(
            UpdateInfo(
                version = tag.removePrefix("v"),
                pageUrl = json.optString("html_url").takeIf { it.startsWith(GITHUB) },
                apkUrl = apk?.takeIf { it.startsWith(GITHUB) },
                notes = json.optString("body").trim().take(600),
            )
        )
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.updateCheck) return Result.success()
        val found = UpdateChecker.check(ctx)
        if (found is UpdateResult.Available && prefs.notifiedVersion != found.info.version) {
            Notifier.postUpdate(ctx, found.info)
            prefs.notifiedVersion = found.info.version
        }
        return Result.success() // never retry hard: a failed check just waits for tomorrow
    }
}

object UpdateScheduler {
    private const val WORK_NAME = "acc_update_check"

    /** Call at startup and whenever the setting changes. Keeps an existing schedule as it is. */
    fun apply(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!Prefs(ctx).updateCheck) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
