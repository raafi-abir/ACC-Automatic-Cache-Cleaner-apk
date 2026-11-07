package io.github.raafiabir.acc

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Runs inside Shizuku's process (shell or root). Keep it dependency-free. */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(script: String): String = try {
        val p = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = thread(isDaemon = true) {
            p.inputStream.bufferedReader().forEachLine { line -> synchronized(out) { out.appendLine(line) } }
        }
        // Never wait forever: a stuck pm call would otherwise freeze the app on "Cleaning".
        if (!p.waitFor(300, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            synchronized(out) { out.appendLine("error: timed out after 300 s") }
        }
        reader.join(2_000)
        val text = synchronized(out) { out.toString() }
        if (text.length > 16_384) text.takeLast(16_384) else text
    } catch (t: Throwable) {
        "error: ${t.message}"
    }
}
