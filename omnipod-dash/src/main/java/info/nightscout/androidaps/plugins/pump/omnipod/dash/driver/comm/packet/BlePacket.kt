package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet

sealed class BlePacket {

    abstract fun asByteArray(): ByteArray
}

data class FirstBlePacket(val totalFragments: Byte, val payload: ByteArray, val size: Byte = 0, val crc32: Long? = null) : BlePacket() {

    override fun asByteArray(): ByteArray {
        TODO("Not yet implemented")
    }
}

data class MiddleBlePacket(val index: Byte, val payload: ByteArray) : BlePacket() {

    override fun asByteArray(): ByteArray {
        TODO("Not yet implemented")
    }
}

data class LastBlePacket(val index: Byte, val size: Byte, val payload: ByteArray, val crc32: Long) : BlePacket() {

    override fun asByteArray(): ByteArray {
        TODO("Not yet implemented")
    }
}

data class LastOptionalPlusOneBlePacket(val index: Byte, val payload: ByteArray) : BlePacket() {

    override fun asByteArray(): ByteArray {
        TODO("Not yet implemented")
    }
}
