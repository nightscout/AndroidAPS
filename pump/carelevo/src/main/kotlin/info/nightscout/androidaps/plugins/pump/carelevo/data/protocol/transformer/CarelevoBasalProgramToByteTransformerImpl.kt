package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolSegmentModel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

class CarelevoBasalProgramToByteTransformerImpl : CarelevoByteTransformer<List<ProtocolSegmentModel>, ByteArray> {

    override fun transform(item: List<ProtocolSegmentModel>): ByteArray {
        return item.map {
            val injectHour = it.injectHour.toByte()
            val injectMin = it.injectMin.toByte()

            val value = it.injectSpeed
            val roundedValue = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()
            val intValue = roundedValue.toInt()
            val decimalValue = ((roundedValue - intValue) * 100).roundToInt()

            byteArrayOf(injectHour, injectMin, intValue.toByte(), decimalValue.toByte())
        }.run {
            var totalSize = 0
            for(array in this) {
                totalSize += array.size
            }

            val result = ByteArray(totalSize)
            var currentIndex = 0

            for(array in this) {
                array.copyInto(result, currentIndex)
                currentIndex += array.size
            }
            result
        }
    }
}