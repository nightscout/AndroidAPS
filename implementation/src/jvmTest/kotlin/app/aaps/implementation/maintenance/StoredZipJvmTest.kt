package app.aaps.implementation.maintenance

import kotlinx.datetime.LocalDateTime
import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** What [StoredZip] writes, read back with `java.util.zip`, which checks every CRC it reads. */
class StoredZipJvmTest {

    @Test
    fun `java util zip reads every entry back`() {
        val random = Random(42)
        val entries = listOf(
            "autotune/aaps-entries.2026-11-15.json" to random.nextBytes(70_000),
            "autotune/empty.log" to ByteArray(0),
            "settings/Upravený profil.json" to "{\"name\":\"ěščř\"}".encodeToByteArray(),
            "settings/deeper/inner.json" to random.nextBytes(3)
        )
        val zip = StoredZip.build(entries, LocalDateTime(2026, 11, 15, 10, 30, 58))

        val read = mutableListOf<Pair<String, ByteArray>>()
        var modified: ZipEntry? = null
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                modified = modified ?: entry
                read += entry.name to input.readBytes()
            }
        }
        assertEquals(entries.map { it.first }, read.map { it.first })
        entries.zip(read).forEach { (written, back) -> assertContentEquals(written.second, back.second, written.first) }
        // DOS time has two second steps.
        assertEquals(java.time.LocalDateTime.of(2026, 11, 15, 10, 30, 58), modified!!.timeLocal)
    }

    @Test
    fun `the crc is the one java util zip computes`() {
        val random = Random(7)
        repeat(50) {
            val data = random.nextBytes(random.nextInt(0, 5000))
            val expected = CRC32().apply { update(data) }.value.toInt()
            assertEquals(expected, StoredZip.crc32(data))
        }
    }
}
