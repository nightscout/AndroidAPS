package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

class CarelevoSubIdToByteTransformerImpl : CarelevoByteTransformer<Int, ByteArray> {

    override fun transform(item: Int): ByteArray {
        if(item != 0 && item != 4 && item != 1) {
            throw IllegalArgumentException("$item is invalid. SubId value must be 0 or 4 or 1")
        }

        runCatching {
            return when(item) {
                4 -> byteArrayOf(0x04.toByte())
                1 -> byteArrayOf(0x01.toByte())
                else -> byteArrayOf(0x00.toByte())
            }
        }.getOrElse {
            throw IllegalArgumentException("$item cannot be parsed")
        }
    }
}