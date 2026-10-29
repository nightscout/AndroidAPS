package app.aaps.plugins.libre3.gatt

/**
 * How a message is cut into pieces on the Bluetooth link, and put back together.
 *
 * Ported from LibreCRKit `BLE/FrameAssembler.swift` at pin `a86b92f`.
 *
 * - What the phone writes: two bytes that say where the piece belongs, then up to 18 bytes.
 * - What the sensor sends: one counting byte, then up to 19 bytes.
 *
 * Everything here is pure, so it is unit tested without a radio.
 */
object Libre3BleFraming {

    const val WRITE_CHUNK_SIZE = 18
    const val NOTIFY_CHUNK_SIZE = 19

    /** Cuts a message into pieces, each with its own place marker in front. */
    fun fragmentForWrite(message: ByteArray, chunkSize: Int = WRITE_CHUNK_SIZE): List<ByteArray> {
        require(chunkSize > 0) { "a piece must hold at least one byte" }
        val out = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < message.size) {
            val end = minOf(offset + chunkSize, message.size)
            out.add(
                byteArrayOf((offset and 0xFF).toByte(), ((offset shr 8) and 0xFF).toByte()) +
                    message.copyOfRange(offset, end)
            )
            offset = end
        }
        return out
    }

    /** Puts written pieces back together. Used by tests and by any later write path. */
    fun reassembleWrite(fragments: List<ByteArray>): ByteArray {
        val pieces = fragments.map { fragment ->
            if (fragment.size < 2) throw Libre3FramingException("a piece must carry its place marker")
            val offset = (fragment[0].toInt() and 0xFF) or ((fragment[1].toInt() and 0xFF) shl 8)
            offset to fragment.copyOfRange(2, fragment.size)
        }.sortedBy { it.first }
        var out = ByteArray(0)
        for ((offset, body) in pieces) {
            if (offset != out.size) throw Libre3FramingException("a piece is missing or out of place")
            out += body
        }
        return out
    }

    /**
     * Puts together what the sensor sends, piece by piece.
     *
     * The counting byte must go up by one every time. A gap means a piece was lost, and the
     * message that would come out of it cannot be trusted.
     */
    class NotifyReassembler {

        private var buffer = ByteArray(0)
        private var nextExpected: Int? = null

        val availableBytes: Int get() = buffer.size

        fun feed(fragment: ByteArray): Int {
            if (fragment.isEmpty()) throw Libre3FramingException("an empty piece cannot be used")
            val sequence = fragment[0].toInt() and 0xFF
            nextExpected?.let {
                if (sequence != it) throw Libre3FramingException("a piece was lost, expected $it but got $sequence")
            }
            nextExpected = (sequence + 1) and 0xFF
            buffer += fragment.copyOfRange(1, fragment.size)
            return buffer.size
        }

        /** Takes the first bytes of the message that was put together. */
        fun take(count: Int): ByteArray {
            if (buffer.size < count) {
                throw Libre3FramingException("only ${buffer.size} bytes are here, $count were asked for")
            }
            val head = buffer.copyOfRange(0, count)
            buffer = buffer.copyOfRange(count, buffer.size)
            return head
        }

        /** Throws everything away and starts counting again. */
        fun reset() {
            buffer = ByteArray(0)
            nextExpected = null
        }
    }
}

/** Raised when pieces of a message do not fit together. */
class Libre3FramingException(message: String) : Exception(message)
