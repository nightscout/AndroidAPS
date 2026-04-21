package app.aaps.pump.carelevo.data.protocol.transformer

class CarelevoReservedToByteTransformerImpl : CarelevoByteTransformer<Boolean, ByteArray> {

    override fun transform(item: Boolean): ByteArray {
        return byteArrayOf(0x00.toByte())
    }
}