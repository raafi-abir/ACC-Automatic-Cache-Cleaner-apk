package io.github.raafiabir.acc

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("acc", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) { sp.edit().putBoolean("setup_done", v).apply() }

    /** Turn the accessibility service off again after each clean (banking apps dislike it being on). */
    var accessibilityOffAfter: Boolean
        get() = sp.getBoolean("a11y_off_after", true)
        set(v) { sp.edit().putBoolean("a11y_off_after", v).apply() }

    /** Accessibility mode: clean every app instead of the 40 largest caches. */
    var a11yAll: Boolean
        get() = sp.getBoolean("a11y_all", false)
        set(v) { sp.edit().putBoolean("a11y_all", v).apply() }

    /** "daily", "weekly", "monthly" or "custom". */
    var repeatMode: String
        get() = sp.getString("repeat_mode", "daily") ?: "daily"
        set(v) { sp.edit().putString("repeat_mode", v).apply() }

    var customDays: Int
        get() = sp.getInt("custom_days", 3)
        set(v) { sp.edit().putInt("custom_days", v.coerceIn(1, 365)).apply() }

    val intervalDays: Int
        get() = when (repeatMode) {
            "weekly" -> 7
            "monthly" -> 30
            "custom" -> customDays.coerceIn(1, 365)
            else -> 1
        }

    var scheduleOn: Boolean
        get() = sp.getBoolean("schedule_on", false)
        set(v) { sp.edit().putBoolean("schedule_on", v).apply() }

    var hour: Int
        get() = sp.getInt("hour", 0)
        set(v) { sp.edit().putInt("hour", v).apply() }

    var minute: Int
        get() = sp.getInt("minute", 0)
        set(v) { sp.edit().putInt("minute", v).apply() }

    var cleanCache: Boolean
        get() = sp.getBoolean("clean_cache", true)
        set(v) { sp.edit().putBoolean("clean_cache", v).apply() }

    var cleanJunk: Boolean
        get() = sp.getBoolean("clean_junk", true)
        set(v) { sp.edit().putBoolean("clean_junk", v).apply() }

    var cleanLeftover: Boolean
        get() = sp.getBoolean("clean_leftover", false)
        set(v) { sp.edit().putBoolean("clean_leftover", v).apply() }

    var backend: Backend
        get() = runCatching { Backend.valueOf(sp.getString("backend", "Auto")!!) }.getOrDefault(Backend.Auto)
        set(v) { sp.edit().putString("backend", v.name).apply() }

    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) { sp.edit().putStringSet("excluded", v).apply() }

    /** 0 means never run. */
    val lastTime: Long get() = sp.getLong("last_time", 0L)
    /** -1 means the size couldn't be measured. */
    val lastFreed: Long get() = sp.getLong("last_freed", -1L)
    /** Output of the last clean script, for bug reports. */
    var lastLog: String
        get() = sp.getString("last_log", "") ?: ""
        set(v) { sp.edit().putString("last_log", v).apply() }

    /** Check GitHub for new releases about once a day. */
    var updateCheck: Boolean
        get() = sp.getBoolean("update_check", true)
        set(v) { sp.edit().putBoolean("update_check", v).apply() }

    val lastUpdateCheck: Long get() = sp.getLong("update_last_check", 0L)
    val updateVersion: String? get() = sp.getString("update_version", null)
    val updatePage: String? get() = sp.getString("update_page", null)
    val updateApk: String? get() = sp.getString("update_apk", null)
    val updateNotes: String get() = sp.getString("update_notes", "") ?: ""
    val updateError: String? get() = sp.getString("update_error", null)

    /** The version we already sent a notification for, so each release notifies once. */
    var notifiedVersion: String?
        get() = sp.getString("update_notified", null)
        set(v) { sp.edit().putString("update_notified", v).apply() }

    fun saveUpdateResult(r: UpdateResult) {
        val e = sp.edit().putLong("update_last_check", System.currentTimeMillis())
        when (r) {
            is UpdateResult.Available -> e
                .putString("update_version", r.info.version).putString("update_page", r.info.pageUrl)
                .putString("update_apk", r.info.apkUrl).putString("update_notes", r.info.notes)
                .remove("update_error")
            UpdateResult.UpToDate -> e
                .remove("update_version").remove("update_page").remove("update_apk").remove("update_notes")
                .remove("update_error")
            is UpdateResult.Failed -> e.putString("update_error", r.message) // keep any known update
        }
        e.apply()
    }

    val lastError: String? get() = sp.getString("last_error", null)

    /** Cache still left after the last clean, -1 if unknown. */
    val lastRemaining: Long get() = sp.getLong("last_remaining", -1L)

    fun saveLast(time: Long, freed: Long, error: String?, remaining: Long = -1L) {
        sp.edit().putLong("last_time", time).putLong("last_freed", freed).putString("last_error", error)
            .putLong("last_remaining", remaining).apply()
    }
}
