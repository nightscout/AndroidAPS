package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

import android.os.Build.VERSION_CODES.P
import kotlin.math.max

class CarelevoIntegerToByteTransformerImpl(
    private val minThreshold : Int,
    private val maxThreshold : Int
) : CarelevoByteTransformer<Int, ByteArray> {

    override fun transform(item: Int): ByteArray {
        if(item !in minThreshold..maxThreshold && !(minThreshold == 0 && maxThreshold == 0)) {
            throw IllegalArgumentException("$item is invalid. Integer value must be greater than $minThreshold, lower than $maxThreshold")
        }

        runCatching {
            return byteArrayOf(item.toByte())
        }.getOrElse {
            throw IllegalArgumentException("$item cannot be parsed")
        }
    }
}