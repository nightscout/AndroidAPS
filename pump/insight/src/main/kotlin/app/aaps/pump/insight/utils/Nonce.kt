package app.aaps.pump.insight.utils

import java.math.BigInteger

class Nonce {

    private var bigInteger: BigInteger

    constructor() {
        bigInteger = BigInteger.ZERO
    }

    constructor(storageValue: ByteArray) {
        bigInteger = BigInteger(storageValue)
    }

    val storageValue: ByteArray
        get() = bigInteger.toByteArray()
    val productionalBytes: ByteBuf
        get() {
            val byteBuf = ByteBuf(13)
            byteBuf.putBytesLE(bigInteger.toByteArray())
            byteBuf.putBytes(0x00.toByte(), 13 - byteBuf.filledSize)
            return byteBuf
        }

    fun increment() {
        bigInteger = bigInteger.add(BigInteger.ONE)
    }

    fun increment(count: Int) {
        bigInteger = bigInteger.add(BigInteger.valueOf(count.toLong()))
    }

    fun isSmallerThan(greater: Nonce): Boolean {
        return bigInteger < greater.bigInteger
    }

    companion object {

        fun fromProductionalBytes(bytes: ByteArray): Nonce {
            val byteBuf = ByteBuf(14)
            byteBuf.putByte(0x00.toByte())
            byteBuf.putBytesLE(bytes)
            return Nonce(byteBuf.bytes)
        }
    }
}