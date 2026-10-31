package io.github.raafiabir.acc

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("acc", Context.MODE_PRIVATE)

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
    val lastError: String? get() = sp.getString("last_error", null)

    fun saveLast(time: Long, freed: Long, error: String?) {
        sp.edit().putLong("last_time", time).putLong("last_freed", freed).putString("last_error", error).apply()
    }
}
