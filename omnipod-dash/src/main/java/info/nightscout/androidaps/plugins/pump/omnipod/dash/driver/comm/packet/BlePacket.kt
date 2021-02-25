package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet

import java.nio.ByteBuffer

sealed class BlePacket {

    abstract fun asByteArray(): ByteArray

    companion object {

        const val MAX_BLE_PACKET_LEN = 30 // we use this as the size allocated for the ByteBuffer
    }
}

data class FirstBlePacket(val totalFragments: Byte, val payload: ByteArray, val size: Byte? = null, val crc32: Long? = null) : BlePacket() {

    override fun asByteArray(): ByteArray {
        val bb = ByteBuffer
            .allocate(MAX_BLE_PACKET_LEN)
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
}

data class MiddleBlePacket(val index: Byte, val payload: ByteArray) : BlePacket() {

    override fun asByteArray(): ByteArray {
        return byteArrayOf(index) + payload
    }
}

data class LastBlePacket(val index: Byte, val size: Byte, val payload: ByteArray, val crc32: Long) : BlePacket() {

    override fun asByteArray(): ByteArray {
        val bb = ByteBuffer
            .allocate(MAX_BLE_PACKET_LEN)
            .put(index)
            .put(size)
            .putInt(crc32.toInt())
            .put(payload)
        val ret = ByteArray(bb.position())
        bb.flip()
        bb.get(ret)
        return ret
    }
}

data class LastOptionalPlusOneBlePacket(val index: Byte, val payload: ByteArray) : BlePacket() {

    override fun asByteArray(): ByteArray {
        return byteArrayOf(index) + payload
    }
}
