package io.github.raafiabir.acc

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuState { NotInstalled, NotRunning, NeedsPermission, Ready }

object ShizukuBridge {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST = 4242

    fun state(ctx: Context): ShizukuState {
        if (!Shizuku.pingBinder()) {
            val installed = runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
            return if (installed) ShizukuState.NotRunning else ShizukuState.NotInstalled
        }
        if (Shizuku.isPreV11()) return ShizukuState.NotRunning
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuState.Ready
        } else {
            ShizukuState.NeedsPermission
        }
    }

    fun requestPermission() = Shizuku.requestPermission(PERMISSION_REQUEST)

    /** After a cold start (e.g. from WorkManager) the binder arrives a moment late. */
    suspend fun awaitBinder(timeoutMs: Long = 6_000): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (!Shizuku.pingBinder()) {
            if (SystemClock.elapsedRealtime() > end) return false
            delay(250)
        }
        return true
    }

    private fun args(ctx: Context) =
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
            .processNameSuffix("shell")
            .daemon(false)
            .version(BuildConfig.VERSION_CODE)

    private suspend fun bind(args: Shizuku.UserServiceArgs): Pair<IShellService, ServiceConnection> =
        try {
            withTimeout(10_000) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (binder != null && binder.pingBinder() && cont.isActive) {
                                cont.resume(IShellService.Stub.asInterface(binder) to this)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {}
                    }
                    Shizuku.bindUserService(args, conn)
                    cont.invokeOnCancellation {
                        runCatching { Shizuku.unbindUserService(args, conn, true) }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Shizuku didn't start the cleaning service")
        }

    /** Runs [script] with shell privileges and returns its output. */
    suspend fun exec(ctx: Context, script: String): String {
        val args = args(ctx)
        val (service, conn) = bind(args)
        try {
            return withContext(Dispatchers.IO) { service.exec(script) }
        } finally {
            runCatching { Shizuku.unbindUserService(args, conn, true) }
        }
    }
}
