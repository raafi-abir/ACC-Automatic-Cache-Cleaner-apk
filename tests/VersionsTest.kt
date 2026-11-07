import io.github.raafiabir.acc.Versions

fun main() {
    var failed = 0
    fun check(name: String, ok: Boolean) { println((if (ok) "ok   " else "FAIL ") + name); if (!ok) failed++ }
    check("1.0.1 is newer than 1.0.0", Versions.isNewer("v1.0.1", "1.0.0"))
    check("1.10.0 is newer than 1.9.9", Versions.isNewer("1.10.0", "1.9.9"))
    check("2.0 is newer than 1.9.9", Versions.isNewer("v2.0", "1.9.9"))
    check("same version is not newer", !Versions.isNewer("v1.0.0", "1.0.0"))
    check("1.0 equals 1.0.0", !Versions.isNewer("1.0", "1.0.0"))
    check("older is not newer", !Versions.isNewer("v1.0.0", "1.2.0"))
    check("pre-release suffix is ignored", !Versions.isNewer("v1.0.0-beta", "1.0.0"))
    check("v1.1 is newer than 1.0", Versions.isNewer("v1.1", "1.0"))
    check("v1.10 is newer than 1.9", Versions.isNewer("v1.10", "1.9"))
    check("v1.0 is not newer than 1.0", !Versions.isNewer("v1.0", "1.0"))
    check("v1.2.1 is newer than 1.2", Versions.isNewer("v1.2.1", "1.2"))
    check("junk tag is never newer", !Versions.isNewer("nightly", "1.0.0"))
    check("empty tag is never newer", !Versions.isNewer("", "1.0.0"))
    if (failed > 0) { println("$failed failed"); kotlin.system.exitProcess(1) }
    println("all passed")
}
