package io.github.raafiabir.acc

import kotlin.system.exitProcess

/** Runs inside Shizuku's process (shell or root). Keep it dependency-free. */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(script: String): String = try {
        val p = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        if (out.length > 16_384) out.takeLast(16_384) else out
    } catch (t: Throwable) {
        "error: ${t.message}"
    }
}
