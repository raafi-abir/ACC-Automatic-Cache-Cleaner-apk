package io.github.raafiabir.acc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class UiState(
    val shizuku: ShizukuState = ShizukuState.NotRunning,
    val backend: Backend = Backend.Auto,
    /** The backend that will actually run, or null if none is usable. */
    val active: Backend? = null,
    val usageAccess: Boolean = true,
    val scanning: Boolean = false,
    val cacheBytes: Long? = null,
    val cacheApps: Int = 0,
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

    /** Re-reads Shizuku state, grants usage access if possible, and rescans. */
    fun refresh() {
        viewModelScope.launch {
            val state = ShizukuBridge.state(ctx)
            val active = Executor.resolve(ctx)
            _ui.update { it.copy(shizuku = state, backend = prefs.backend, active = active) }

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
        _ui.update { s ->
            val counted = map?.filter { it.key !in s.excluded && it.key != ctx.packageName }
            s.copy(
                scanning = false,
                usageAccess = access,
                cacheBytes = counted?.values?.sum(),
                cacheApps = counted?.count { it.value > 0 } ?: 0,
            )
        }
    }

    fun requestPermission() = ShizukuBridge.requestPermission()

    fun clean() {
        if (_ui.value.cleaning) return
        viewModelScope.launch {
            if (Executor.resolve(ctx) == null) {
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
            _ui.update { it.copy(cleaning = true, message = null) }
            val result = Cleaner.runRecorded(ctx)
            _ui.update {
                it.copy(
                    cleaning = false,
                    lastTime = prefs.lastTime,
                    lastFreed = prefs.lastFreed,
                    lastError = prefs.lastError,
                )
            }
            scan()
            result.onFailure { e -> _ui.update { it.copy(message = e.message ?: "Cleaning failed") } }
        }
    }

    fun setBackend(b: Backend) {
        prefs.backend = b
        _ui.update { it.copy(backend = b) }
        refresh()
    }

    fun cycleBackend() = setBackend(prefs.backend.next())

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
