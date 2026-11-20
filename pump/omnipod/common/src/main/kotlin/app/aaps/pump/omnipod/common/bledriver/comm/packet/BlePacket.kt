package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.comm.message.IncorrectPacketException
import java.nio.ByteBuffer

sealed class BlePacket {

    abstract val payload: ByteArray
    abstract fun toByteArray(layout: BlePacketLayout): ByteArray
}

data class FirstBlePacket(
    val fullFragments: Int,
    override val payload: ByteArray,
    val size: Byte? = null,
    val crc32: Long? = null,
    val oneExtraPacket: Boolean = false
) : BlePacket() {

    override fun toByteArray(layout: BlePacketLayout): ByteArray {
        val bb = ByteBuffer
            .allocate(layout.maxPayloadSize)
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
        val ret = ByteArray(if (layout.padToMaxPayloadSize) layout.maxPayloadSize else pos)
        bb.flip()
        bb.get(ret, 0, pos)

        return ret
    }

    companion object {

        fun parse(payload: ByteArray, layout: BlePacketLayout): FirstBlePacket {
            payload.assertSizeAtLeast(HEADER_SIZE_WITH_MIDDLE_PACKETS, 0)

            if (payload[0].toInt() != 0) {
                // most likely we lost the first packet.
                throw IncorrectPacketException(payload, 0)
            }
            val fullFragments = payload[1].toUnsignedInt()
            require(fullFragments < layout.maxFragments) { "Received more than ${layout.maxFragments} fragments" }

            payload.assertSizeAtLeast(HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, 0)

            return when {

                fullFragments == 0      -> {
                    val rest = payload[6]
                    val restUnsigned = rest.toUnsignedInt()
                    val end = Integer.min(restUnsigned + HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, payload.size)
                    payload.assertSizeAtLeast(end, 0)
                    FirstBlePacket(
                        fullFragments = fullFragments,
                        payload = payload.copyOfRange(HEADER_SIZE_WITHOUT_MIDDLE_PACKETS, end),
                        crc32 = ByteBuffer.wrap(payload.copyOfRange(2, 6)).int.toUnsignedLong(),
                        size = rest,
                        oneExtraPacket = restUnsigned + HEADER_SIZE_WITHOUT_MIDDLE_PACKETS > end
                    )
                }

                // With middle packets
                payload.size < layout.maxPayloadSize ->
                    throw IncorrectPacketException(payload, 0)

                else                    -> {
                    FirstBlePacket(
                        fullFragments = fullFragments,
                        payload = payload.copyOfRange(HEADER_SIZE_WITH_MIDDLE_PACKETS, layout.maxPayloadSize)
                    )
                }
            }
        }

        private const val HEADER_SIZE_WITHOUT_MIDDLE_PACKETS = 7 // we are using all fields
        private const val HEADER_SIZE_WITH_MIDDLE_PACKETS = 2
    }
}

data class MiddleBlePacket(val index: Byte, override val payload: ByteArray) : BlePacket() {

    override fun toByteArray(layout: BlePacketLayout): ByteArray {
        return byteArrayOf(index) + payload
    }

    companion object {

        fun parse(payload: ByteArray, layout: BlePacketLayout): MiddleBlePacket {
            payload.assertSizeAtLeast(layout.maxPayloadSize)
            return MiddleBlePacket(
                index = payload[0],
                payload.copyOfRange(1, layout.maxPayloadSize)
            )
        }
    }
}

data class LastBlePacket(
    val index: Byte,
    val size: Byte,
    override val payload: ByteArray,
    val crc32: Long,
    val oneExtraPacket: Boolean = false
) : BlePacket() {

    override fun toByteArray(layout: BlePacketLayout): ByteArray {
        val bb = ByteBuffer
            .allocate(layout.maxPayloadSize)
            .put(index)
            .put(size)
            .putInt(crc32.toInt())
            .put(payload)
        val pos = bb.position()
        val ret = ByteArray(if (layout.padToMaxPayloadSize) layout.maxPayloadSize else pos)
        bb.flip()
        bb.get(ret, 0, pos)
        return ret
    }

    companion object {

        fun parse(payload: ByteArray, layout: BlePacketLayout): LastBlePacket {
            payload.assertSizeAtLeast(HEADER_SIZE)

            val rest = payload[1]
            val restUnsigned = rest.toUnsignedInt()
            val end = Integer.min(restUnsigned + HEADER_SIZE, payload.size)

            payload.assertSizeAtLeast(end)

            return LastBlePacket(
                index = payload[0],
                crc32 = ByteBuffer.wrap(payload.copyOfRange(2, 6)).int.toUnsignedLong(),
                oneExtraPacket = restUnsigned + HEADER_SIZE > end,
                size = rest,
                payload = payload.copyOfRange(HEADER_SIZE, end)
            )
        }

        private const val HEADER_SIZE = 6
    }
}

data class LastOptionalPlusOneBlePacket(
    val index: Byte,
    override val payload: ByteArray,
    val size: Byte
) : BlePacket() {

    override fun toByteArray(layout: BlePacketLayout): ByteArray {
        val exact = byteArrayOf(index, size) + payload
        return if (layout.padToMaxPayloadSize) exact + ByteArray(layout.maxPayloadSize - exact.size) else exact
    }

    companion object {

        fun parse(payload: ByteArray, layout: BlePacketLayout): LastOptionalPlusOneBlePacket {
            payload.assertSizeAtLeast(2)
            val size = payload[1].toUnsignedInt()
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

/**
 * Reinterprets this Byte's bit pattern as an unsigned value (0..255), matching the on-wire
 * meaning of every size/count/index byte in this protocol. Kotlin's Byte is signed, so plain
 * arithmetic/comparison on it silently sign-extends (e.g. wire value 200 reads back as -56) -
 * this is needed anywhere such a byte feeds into arithmetic or a comparison, not just storage.
 */
internal fun Byte.toUnsignedInt(): Int = this.toInt() and 0xFF
