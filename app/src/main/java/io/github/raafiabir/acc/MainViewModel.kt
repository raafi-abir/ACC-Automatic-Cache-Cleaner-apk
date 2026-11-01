package io.github.raafiabir.acc

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** One of the largest caches, for the bar on the home screen. */
data class Slice(val name: String, val bytes: Long)

data class UiState(
    val setupDone: Boolean = false,
    val shizuku: ShizukuState = ShizukuState.NotRunning,
    val backend: Backend = Backend.Auto,
    /** The backend that will actually run, or null if none is usable. */
    val active: Backend? = null,
    val usageAccess: Boolean = true,
    val notifications: Boolean = true,
    val scanning: Boolean = false,
    val cacheBytes: Long? = null,
    val cacheApps: Int = 0,
    val top: List<Slice> = emptyList(),
    val cleaning: Boolean = false,
    val progress: String? = null,
    val lastTime: Long = 0L,
    val lastFreed: Long = -1L,
    val lastError: String? = null,
    val scheduleOn: Boolean = false,
    val hour: Int = 0,
    val minute: Int = 0,
    val cleanCache: Boolean = true,
    val cleanJunk: Boolean = true,
    val cleanLeftover: Boolean = false,
    val excluded: Set<String> = emptySet(),
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val prefs = Prefs(app)
    private val scanner = CacheScanner(app)

    private val _ui = MutableStateFlow(fromPrefs())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        viewModelScope.launch {
            AccessibilityCleaner.progress.collect { p -> _ui.update { it.copy(progress = p) } }
        }
        viewModelScope.launch {
            RunState.running.collect { running ->
                val was = _ui.value.cleaning
                _ui.update { it.copy(cleaning = running) }
                if (was && !running) {
                    _ui.update {
                        it.copy(lastTime = prefs.lastTime, lastFreed = prefs.lastFreed, lastError = prefs.lastError)
                    }
                    scan()
                }
            }
        }
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPermission)
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPermission)
    }

    private fun fromPrefs() = UiState(
        setupDone = prefs.setupDone,
        backend = prefs.backend,
        lastTime = prefs.lastTime,
        lastFreed = prefs.lastFreed,
        lastError = prefs.lastError,
        scheduleOn = prefs.scheduleOn,
        hour = prefs.hour,
        minute = prefs.minute,
        cleanCache = prefs.cleanCache,
        cleanJunk = prefs.cleanJunk,
        cleanLeftover = prefs.cleanLeftover,
        excluded = prefs.excluded,
    )

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Re-reads access state, grants usage access if possible, and rescans. */
    fun refresh() {
        viewModelScope.launch {
            val state = ShizukuBridge.state(ctx)
            val active = Executor.resolve(ctx)
            _ui.update {
                it.copy(shizuku = state, backend = prefs.backend, active = active, notifications = notificationsGranted())
            }

            // Shell backends can grant usage access themselves; accessibility mode needs you to.
            if (active != null && active != Backend.Accessibility && !scanner.hasUsageAccess()) {
                runCatching {
                    Executor.exec(ctx, active, "appops set ${ctx.packageName} GET_USAGE_STATS allow")
                }
            }
            scan()
        }
    }

    private suspend fun scan() {
        _ui.update { it.copy(scanning = true) }
        val access = scanner.hasUsageAccess()
        val map = if (access) scanner.scanCaches() else null
        val excluded = _ui.value.excluded
        val counted = map?.filter { it.key !in excluded && it.key != ctx.packageName && it.value > 0 }
        val top = if (counted != null) {
            withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                counted.entries.sortedByDescending { it.value }.take(3).map { e ->
                    val name = runCatching { pm.getApplicationInfo(e.key, 0).loadLabel(pm).toString() }
                        .getOrDefault(e.key)
                    Slice(name, e.value)
                }
            }
        } else emptyList()
        _ui.update {
            it.copy(
                scanning = false,
                usageAccess = access,
                cacheBytes = counted?.values?.sum(),
                cacheApps = counted?.size ?: 0,
                top = top,
            )
        }
    }

    fun requestPermission() = ShizukuBridge.requestPermission()

    fun clean() {
        if (_ui.value.cleaning) return
        viewModelScope.launch {
            val backend = Executor.resolve(ctx)
            if (backend == null) {
                val askShizuku = prefs.backend != Backend.Root &&
                    ShizukuBridge.state(ctx) == ShizukuState.NeedsPermission
                if (askShizuku) {
                    requestPermission()
                } else {
                    _ui.update { it.copy(message = "Nothing to clean with yet. See the status above.") }
                    refresh()
                }
                return@launch
            }
            _ui.update { it.copy(message = null) }
            if (backend == Backend.Accessibility) {
                CleaningService.start(ctx) // long run: lives in a foreground service
            } else {
                Cleaner.runRecorded(ctx)
            }
        }
    }

    fun stop() = CleaningService.stop(ctx)

    fun finishSetup() {
        prefs.setupDone = true
        _ui.update { it.copy(setupDone = true) }
    }

    fun setBackend(b: Backend) {
        prefs.backend = b
        _ui.update { it.copy(backend = b) }
        refresh()
    }

    fun recheckRoot() {
        viewModelScope.launch {
            RootBridge.available(force = true)
            refresh()
        }
    }

    fun setSchedule(on: Boolean) {
        prefs.scheduleOn = on
        _ui.update { it.copy(scheduleOn = on) }
        Scheduler.apply(ctx)
    }

    fun setTime(hour: Int, minute: Int) {
        prefs.hour = hour
        prefs.minute = minute
        _ui.update { it.copy(hour = hour, minute = minute) }
        Scheduler.apply(ctx)
    }

    fun setCache(on: Boolean) { prefs.cleanCache = on; _ui.update { it.copy(cleanCache = on) } }
    fun setJunk(on: Boolean) { prefs.cleanJunk = on; _ui.update { it.copy(cleanJunk = on) } }
    fun setLeftover(on: Boolean) { prefs.cleanLeftover = on; _ui.update { it.copy(cleanLeftover = on) } }

    fun toggleExcluded(pkg: String) {
        val next = prefs.excluded.let { if (pkg in it) it - pkg else it + pkg }
        prefs.excluded = next
        _ui.update { it.copy(excluded = next) }
        viewModelScope.launch { scan() }
    }

    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch { _apps.value = scanner.installedApps() }
    }
}
