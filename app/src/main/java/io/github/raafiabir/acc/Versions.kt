package io.github.raafiabir.acc

/** Plain version-string comparison, no Android classes so it can be tested anywhere. */
object Versions {
    /** "v1.2.10-beta" gives [1, 2, 10]. */
    fun parts(version: String): List<Int> =
        Regex("\\d+")
            .findAll(version.trim().removePrefix("v").substringBefore('-').substringBefore('+'))
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
