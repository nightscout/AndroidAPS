package app.aaps.implementation.maintenance

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

/**
 * Writes a zip file whose entries are stored, not compressed.
 *
 * For iOS, which has no zip API to call: Foundation can only zip a whole folder, and only through a
 * file coordinator meant for uploads. Storing keeps this small - no deflate, just the headers and a
 * CRC-32 - and any unzip tool reads it. The cost is size: text is kept as it is. Android and the
 * desktop deflate with `java.util.zip` and do not use this.
 *
 * It is common code so that `StoredZipTest` can read what it writes with `java.util.zip` on the JVM.
 * Names are written as UTF-8 and marked so, because a file name can come from a translated string.
 * Limited to what a zip without the ZIP64 extension holds: fewer than 65535 entries and 4 GB.
 */
internal object StoredZip {

    fun build(entries: List<Pair<String, ByteArray>>, modified: LocalDateTime): ByteArray {
        require(entries.size < 0xFFFF) { "too many entries for a zip without ZIP64" }
        val out = Bytes()
        val central = Bytes()
        val time = (modified.hour shl 11) or (modified.minute shl 5) or (modified.second / 2)
        val date = ((modified.year - 1980).coerceIn(0, 127) shl 9) or (modified.month.number shl 5) or modified.day
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            out.u32(0x04034b50)
            out.u16(VERSION)
            out.u16(UTF8_NAMES)
            out.u16(0) // stored
            out.u16(time)
            out.u16(date)
            out.u32(crc)
            out.u32(data.size)
            out.u32(data.size)
            out.u16(nameBytes.size)
            out.u16(0)
            out.bytes(nameBytes)
            out.bytes(data)

            central.u32(0x02014b50)
            central.u16(VERSION)
            central.u16(VERSION)
            central.u16(UTF8_NAMES)
            central.u16(0)
            central.u16(time)
            central.u16(date)
            central.u32(crc)
            central.u32(data.size)
            central.u32(data.size)
            central.u16(nameBytes.size)
            central.u16(0) // extra
            central.u16(0) // comment
            central.u16(0) // disk
            central.u16(0) // internal attributes
            central.u32(0) // external attributes
            central.u32(offset)
            central.bytes(nameBytes)
        }
        val centralOffset = out.size
        out.bytes(central.toByteArray())
        out.u32(0x06054b50)
        out.u16(0)
        out.u16(0)
        out.u16(entries.size)
        out.u16(entries.size)
        out.u32(central.size)
        out.u32(centralOffset)
        out.u16(0)
        return out.toByteArray()
    }

    /** CRC-32 as zip uses it (polynomial 0xEDB88320). */
    fun crc32(data: ByteArray): Int {
        var crc = 0.inv()
        for (b in data) crc = TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }

    private const val VERSION = 20
    private const val UTF8_NAMES = 0x0800

    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    /** A growing byte buffer, little endian as zip is. */
    private class Bytes {

        private var data = ByteArray(4096)
        var size = 0
            private set

        private fun room(count: Int) {
            if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
        }

        fun u16(value: Int) {
            room(2)
            data[size++] = value.toByte()
            data[size++] = (value ushr 8).toByte()
        }

        fun u32(value: Int) {
            room(4)
            for (shift in 0..24 step 8) data[size++] = (value ushr shift).toByte()
        }

        fun bytes(bytes: ByteArray) {
            room(bytes.size)
            bytes.copyInto(data, size)
            size += bytes.size
        }

        fun toByteArray(): ByteArray = data.copyOf(size)
    }
}
