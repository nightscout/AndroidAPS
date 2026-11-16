package app.aaps.implementation.maintenance

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [StoredZip] on every target, iOS included, where it is the only zip writer.
 *
 * `StoredZipJvmTest` reads the result with `java.util.zip`; these fixed answers hold on any target.
 */
class StoredZipCommonTest {

    @Test
    fun crcOfTheStandardCheckText() {
        // The check value every CRC-32 implementation is tested with.
        assertEquals(0xCBF43926.toInt(), StoredZip.crc32("123456789".encodeToByteArray()))
        assertEquals(0, StoredZip.crc32(ByteArray(0)))
    }

    @Test
    fun anEmptyZipIsOnlyTheEndRecord() {
        val zip = StoredZip.build(emptyList(), LocalDateTime(2026, 11, 15, 10, 30, 0))
        assertEquals(22, zip.size)
        assertEquals(listOf(0x50, 0x4b, 0x05, 0x06), zip.take(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun theLayoutOfOneEntry() {
        val data = "abc".encodeToByteArray()
        val zip = StoredZip.build(listOf("autotune/a.json" to data), LocalDateTime(2026, 11, 15, 10, 30, 0))
        // Local header 30 + name 15 + data 3, central header 46 + name 15, end record 22.
        assertEquals(30 + 15 + 3 + 46 + 15 + 22, zip.size)
        assertEquals(listOf(0x50, 0x4b, 0x03, 0x04), zip.take(4).map { it.toInt() and 0xFF })
        assertEquals("autotune/a.json", zip.copyOfRange(30, 45).decodeToString())
        assertEquals("abc", zip.copyOfRange(45, 48).decodeToString())
    }
}
