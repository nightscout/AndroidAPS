package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet

import java.nio.ByteBuffer

sealed class BlePacket {

    abstract fun asByteArray(): ByteArray

    companion object {

        const val MAX_BLE_PACKET_LEN = 20
        const val MAX_BLE_BUFFER_LEN = MAX_BLE_PACKET_LEN + 1 // we use this as the size allocated for the ByteBuffer
    }
}

data class FirstBlePacket(val totalFragments: Byte, val payload: ByteArray, val size: Byte? = null, val crc32: Long? = null) : BlePacket() {

    override fun asByteArray(): ByteArray {
        val bb = ByteBuffer
            .allocate(MAX_BLE_BUFFER_LEN)
            .put(0) // index
            .put(totalFragments) // # of fragments except FirstBlePacket and LastOptionalPlusOneBlePacket
        crc32?.let {
            bb.putInt(crc32.toInt())
        }
        size?.let {
            bb.put(size)
        }
        bb.put(payload)
        val ret = ByteArray(bb.position())
        bb.flip()
        bb.get(ret)
        return ret
    }

    companion object {

        internal const val CAPACITY_WITHOUT_MIDDLE_PACKETS = 13 // we are using all fields
        internal const val CAPACITY_WITH_MIDDLE_PACKETS = 18 // we are not using crc32 or size
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
            .allocate(MAX_BLE_BUFFER_LEN)
            .put(index)
            .put(size)
            .putInt(crc32.toInt())
            .put(payload)
        val ret = ByteArray(bb.position())
        bb.flip()
        bb.get(ret)
        return ret
    }

    companion object {

        internal const val CAPACITY = 14
    }
}

data class LastOptionalPlusOneBlePacket(val index: Byte, val payload: ByteArray, val size: Byte) : BlePacket() {

    override fun asByteArray(): ByteArray {
        return byteArrayOf(index, size) + payload
    }
}

