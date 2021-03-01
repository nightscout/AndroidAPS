package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet

import java.nio.ByteBuffer

sealed class BlePacket {

    abstract fun asByteArray(): ByteArray

    companion object {

        const val MAX_SIZE = 20
    }
}

data class FirstBlePacket(val totalFragments: Byte, val payload: ByteArray, val size: Byte? = null, val crc32: Long? = null) : BlePacket() {

    override fun asByteArray(): ByteArray {
        val bb = ByteBuffer
            .allocate(MAX_SIZE)
            .put(0) // index
            .put(totalFragments) // # of fragments except FirstBlePacket and LastOptionalPlusOneBlePacket
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

        internal const val HEADER_SIZE_WITHOUT_MIDDLE_PACKETS = 7 // we are using all fields
        internal const val HEADER_SIZE_WITH_MIDDLE_PACKETS = 2

        internal const val CAPACITY_WITHOUT_MIDDLE_PACKETS = MAX_SIZE - HEADER_SIZE_WITHOUT_MIDDLE_PACKETS // we are using all fields
        internal const val CAPACITY_WITH_MIDDLE_PACKETS = MAX_SIZE - HEADER_SIZE_WITH_MIDDLE_PACKETS // we are not using crc32 or size
        internal const val CAPACITY_WITH_THE_OPTIONAL_PLUS_ONE_PACKET = 18
    }
}

data class MiddleBlePacket(val index: Byte, val payload: ByteArray) : BlePacket() {

    override fun asByteArray(): ByteArray {
        return byteArrayOf(index) + payload
    }

    companion object {

        internal const val CAPACITY = 19
    }
}

data class LastBlePacket(val index: Byte, val size: Byte, val payload: ByteArray, val crc32: Long) : BlePacket() {

    override fun asByteArray(): ByteArray {
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

        internal const val HEADER_SIZE = 6
        internal const val CAPACITY = MAX_SIZE - HEADER_SIZE
    }
}

data class LastOptionalPlusOneBlePacket(val index: Byte, val payload: ByteArray, val size: Byte) : BlePacket() {

    override fun asByteArray(): ByteArray {
        return byteArrayOf(index, size) + payload + ByteArray(MAX_SIZE - payload.size - 2)
    }

    companion object {

        internal const val HEADER_SIZE = 2
    }
}
