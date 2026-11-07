package io.github.raafiabir.acc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Direct root through the `su` binary (Magisk, KernelSU, APatch). */
object RootBridge {
    @Volatile
    private var cached: Boolean? = null

    /** The first call shows your root manager's prompt. The result is kept until [force]d. */
    suspend fun available(force: Boolean = false): Boolean {
        if (!force) cached?.let { return it }
        val ok = try {
            run("id", 20_000).contains("uid=0")
        } catch (e: java.io.IOException) {
            false // no su binary
        }
        cached = ok
        return ok
    }

    suspend fun exec(script: String): String = run(script, 180_000)

    private suspend fun run(script: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        // A blocking read can't be cancelled, so a watchdog kills su if it hangs (e.g. unanswered prompt).
        val watchdog = launch { delay(timeoutMs); p.destroy() }
        try {
            p.outputStream.bufferedWriter().use { it.write(script); it.write("\nexit\n") }
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } finally {
            watchdog.cancel()
        }
    }
}
