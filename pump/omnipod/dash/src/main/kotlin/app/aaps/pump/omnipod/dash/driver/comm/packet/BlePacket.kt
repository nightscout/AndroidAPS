package app.aaps.pump.omnipod.dash.driver.comm.packet

import app.aaps.pump.omnipod.dash.driver.comm.message.IncorrectPacketException
import java.nio.ByteBuffer

sealed class BlePacket {

    abstract val payload: ByteArray
    abstract fun toByteArray(): ByteArray

    companion object {

        const val MAX_SIZE = 20
    }
}

data class FirstBlePacket(
    val fullFragments: Int,
    override val payload: ByteArray,
    val size: Byte? = null,
    val crc32: Long? = null,
    val oneExtraPacket: Boolean = false
) : BlePacket() {

    override fun toByteArray(): ByteArray {
        val bb = ByteBuffer
            .allocate(MAX_SIZE)
            .put(0) // index
            .put(fullFragments.toByte()) // # of fragments except FirstBlePacket and LastOptionalPlusOneBlePacket
        crc32?.let {
            bb.putInt(crc32.toInt())
        }
        size?.let {
            bb.put(size)
        }
        bb.put(payload)

        val pos = bb.position()
        val ret = ByteArray(MAX_SIZE)
        bb.flip()
        bb.get(ret, 0, pos)

        return ret
    }

    companion object {

        fun parse(payload: ByteArray): FirstBlePacket {
            payload.assertSizeAtLeast(HEADER_SIZE_WITH_MIDDLE_PACKETS, 0)

            if (payload[0].toInt() != 0) {
                // most likely we lost the first packet.
                throw IncorrectPacketException(payload, 0)
            }
            val fullFragments = payload[1].toInt()
            require(fullFragments < MAX_FRAGMENTS) { "Received more than $MAX_FRAGMENTS fragments" }

            payload.assertSizeAtLeast(HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, 0)

            return when {

                fullFragments == 0      -> {
                    val rest = payload[6]
                    val end = Integer.min(rest + HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, payload.size)
                    payload.assertSizeAtLeast(end, 0)
                    FirstBlePacket(
                        fullFragments = fullFragments,
                        payload = payload.copyOfRange(HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, end),
                        crc32 = ByteBuffer.wrap(payload.copyOfRange(2, 6)).int.toUnsignedLong(),
                        size = rest,
                        oneExtraPacket = rest + HEADER_SIZE_WITHOUT_MIDDLE_PACKETS > end
                    )
                }

                // With middle packets
                payload.size < MAX_SIZE ->
                    throw IncorrectPacketException(payload, 0)

                else                    -> {
                    FirstBlePacket(
                        fullFragments = fullFragments,
                        payload = payload.copyOfRange(HEADER_SIZE_WITH_MIDDLE_PACKETS, MAX_SIZE)
                    )
                }
            }
        }

        private const val HEADER_SIZE_WITHOUT_MIDDLE_PACKETS = 7 // we are using all fields
        private const val HEADER_SIZE_WITH_MIDDLE_PACKETS = 2

        internal const val CAPACITY_WITHOUT_MIDDLE_PACKETS =
            MAX_SIZE - HEADER_SIZE_WITHOUT_MIDDLE_PACKETS // we are using all fields
        internal const val CAPACITY_WITH_MIDDLE_PACKETS =
            MAX_SIZE - HEADER_SIZE_WITH_MIDDLE_PACKETS // we are not using crc32 or size
        internal const val CAPACITY_WITH_THE_OPTIONAL_PLUS_ONE_PACKET = 18

        private const val MAX_FRAGMENTS = 15 // 15*20=300 bytes
    }
}

data class MiddleBlePacket(val index: Byte, override val payload: ByteArray) : BlePacket() {

    override fun toByteArray(): ByteArray {
        return byteArrayOf(index) + payload
    }

    companion object {

        fun parse(payload: ByteArray): MiddleBlePacket {
            payload.assertSizeAtLeast(MAX_SIZE)
            return MiddleBlePacket(
                index = payload[0],
                payload.copyOfRange(1, MAX_SIZE)
            )
        }

        internal const val CAPACITY = 19
    }
}

data class LastBlePacket(
    val index: Byte,
    val size: Byte,
    override val payload: ByteArray,
    val crc32: Long,
    val oneExtraPacket: Boolean = false
) : BlePacket() {

    override fun toByteArray(): ByteArray {
        val bb = ByteBuffer
            .allocate(MAX_SIZE)
            .put(index)
            .put(size)
            .putInt(crc32.toInt())
            .put(payload)
        val pos = bb.position()
        val ret = ByteArray(MAX_SIZE)
        bb.flip()
        bb.get(ret, 0, pos)
        return ret
    }

    companion object {

        fun parse(payload: ByteArray): LastBlePacket {
            payload.assertSizeAtLeast(HEADER_SIZE)

            val rest = payload[1]
            val end = Integer.min(rest + HEADER_SIZE, payload.size)

            payload.assertSizeAtLeast(end)

            return LastBlePacket(
                index = payload[0],
                crc32 = ByteBuffer.wrap(payload.copyOfRange(2, 6)).int.toUnsignedLong(),
                oneExtraPacket = rest + HEADER_SIZE > end,
                size = rest,
                payload = payload.copyOfRange(HEADER_SIZE, end)
            )
        }

        private const val HEADER_SIZE = 6
        internal const val CAPACITY = MAX_SIZE - HEADER_SIZE
    }
}

data class LastOptionalPlusOneBlePacket(
    val index: Byte,
    override val payload: ByteArray,
    val size: Byte
) : BlePacket() {

    override fun toByteArray(): ByteArray {
        return byteArrayOf(index, size) + payload + ByteArray(MAX_SIZE - payload.size - 2)
    }

    companion object {

        fun parse(payload: ByteArray): LastOptionalPlusOneBlePacket {
            payload.assertSizeAtLeast(2)
            val size = payload[1].toInt()
            payload.assertSizeAtLeast(HEADER_SIZE + size)

            return LastOptionalPlusOneBlePacket(
                index = payload[0],
                payload = payload.copyOfRange(
                    HEADER_SIZE,
                    HEADER_SIZE + size
                ),
                size = size.toByte(),
            )
        }

        private const val HEADER_SIZE = 2
    }
}

private fun ByteArray.assertSizeAtLeast(size: Int, index: Byte? = null) {
    if (this.size < size) {
        throw IncorrectPacketException(this, index)
    }
}
