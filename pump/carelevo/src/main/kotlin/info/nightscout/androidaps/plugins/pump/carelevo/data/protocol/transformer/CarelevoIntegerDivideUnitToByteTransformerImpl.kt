package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

import kotlin.math.max

class CarelevoIntegerDivideUnitToByteTransformerImpl(
    private val minThreshold : Int,
    private val maxThreshold : Int
) : CarelevoByteTransformer<Int, ByteArray> {

    override fun transform(item: Int): ByteArray {
        if(item !in minThreshold..maxThreshold && !(minThreshold == 0 && maxThreshold == 0)) {
            throw IllegalArgumentException("$item is invalid. Integer value must be greater than $minThreshold, lower than $maxThreshold")
        }

        runCatching {
            val quotient = item / 100
            val remain = item % 100

            return byteArrayOf(quotient.toByte(), remain.toByte())
        }.getOrElse {
            throw IllegalArgumentException("$item cannot be parsed")
        }
    }
}