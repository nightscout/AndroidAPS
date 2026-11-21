package app.aaps.plugins.eversense.protocol

import org.junit.jupiter.api.Assertions.fail
import java.io.File
import java.security.MessageDigest

/**
 * Pins what the protocol code produces for large sets of inputs to digests recorded from the first driver (the frozen
 * sources the compatibility tests compared against, 2026-11-21). Each group of outputs becomes one SHA-256, stored in
 * `src/androidHostTest/resources/golden/<name>.txt` as `group|digest`, so a change names the group it is in.
 *
 * On a difference the actual digests are written to `build/golden-actual/<name>.txt`. Copy them over the recorded
 * file only for a change made on purpose, after checking it against a real transmitter.
 */
internal class GoldenDigests(private val name: String) {

    private val digests = linkedMapOf<String, MessageDigest>()

    fun add(group: String, value: Any?) {
        digests.getOrPut(group) { MessageDigest.getInstance("SHA-256") }.update("${text(value)}\n".toByteArray())
    }

    /** Checks the groups this test added. Several tests can share one file, each with its own groups. */
    fun check() {
        val actual = digests.mapValues { (_, digest) -> digest.digest().joinToString("") { "%02x".format(it) } }
        val expected = parse(javaClass.getResourceAsStream("/golden/$name.txt")?.bufferedReader()?.use { it.readLines() }.orEmpty())
        val differing = actual.keys.filter { actual[it] != expected[it] }
        if (differing.isEmpty()) return
        // Merged, so the tests that share the file all leave their digests in it
        val file = File("build/golden-actual/$name.txt").apply { parentFile.mkdirs() }
        val merged = (if (file.exists()) parse(file.readLines()) else emptyMap()) + actual
        file.writeText(merged.toSortedMap().entries.joinToString("\n", postfix = "\n") { "${it.key}|${it.value}" })
        fail<Unit>("Outputs differ from the recorded first driver in $differing; actual digests in ${file.absolutePath}")
    }

    private fun parse(lines: List<String>): Map<String, String> =
        lines.filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }

    private fun text(value: Any?): String = when (value) {
        is ByteArray  -> value.joinToString("") { "%02x".format(it) }
        is UByteArray -> value.joinToString("") { "%02x".format(it.toInt()) }
        is Collection<*> -> value.joinToString(",", "[", "]") { text(it) }
        else          -> value.toString()
    }
}
