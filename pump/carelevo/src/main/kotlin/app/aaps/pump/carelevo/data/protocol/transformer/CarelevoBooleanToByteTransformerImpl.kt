package app.aaps.pump.carelevo.data.protocol.transformer

class CarelevoBooleanToByteTransformerImpl : CarelevoByteTransformer<Boolean, ByteArray> {

    override fun transform(item: Boolean): ByteArray {
        return if (item) {
            byteArrayOf(0x00.toByte())
        } else {
            byteArrayOf(0x01.toByte())
        }
    }
}