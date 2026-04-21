package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

class CarelevoDoubleToByteTransformerImpl(
    private val minThreshold : Double,
    private val maxThreshold : Double
) : CarelevoByteTransformer<Double, ByteArray> {

    override fun transform(item: Double): ByteArray {
        if(item !in minThreshold..maxThreshold && !(minThreshold==0.0 && maxThreshold == 0.0)) {
            throw IllegalArgumentException("$item is invalid. Double value must be greater than $minThreshold, lower than $maxThreshold")
        }
        runCatching {
            val multiplier = 100
            val roundedValue = BigDecimal(item).setScale(2, RoundingMode.HALF_UP).toDouble()

            val intValue = roundedValue.toInt()
            val decimalValue = ((roundedValue - intValue) * multiplier).roundToInt()

            return byteArrayOf(intValue.toByte(), decimalValue.toByte())
        }.getOrElse {
            throw IllegalArgumentException("$item cannot be parsed")
        }
    }
}