package app.aaps.pump.medtronic.data.dto

import com.google.gson.annotations.Expose
import app.aaps.pump.medtronic.defs.BatteryType
import java.util.*

/**
 * Created by andy on 6/14/18.
 */
class BatteryStatusDTO {

    @Expose
    var batteryStatusType: BatteryStatusType? = null
    @Expose
    var voltage: Double? = null

    var extendedDataReceived = false

    fun getCalculatedPercent(batteryType: BatteryType): Int {
        if (voltage == null || batteryType === BatteryType.None) {
            return if (batteryStatusType == BatteryStatusType.Low || batteryStatusType == BatteryStatusType.Unknown) 18 else 70
        }
        val percent = (voltage!! - batteryType.lowVoltage) / (batteryType.highVoltage - batteryType.lowVoltage)
        var percentInt = (percent * 100.0).toInt()
        if (percentInt < 0) percentInt = 1
        if (percentInt > 100) percentInt = 100
        return percentInt
    }

    override fun toString(): String {
        return String.format(Locale.ENGLISH, "BatteryStatusDTO [voltage=%.2f, alkaline=%d, lithium=%d, niZn=%d, nimh=%d]",
            if (voltage == null) 0.0f else voltage,
            getCalculatedPercent(BatteryType.Alkaline),
            getCalculatedPercent(BatteryType.Lithium),
            getCalculatedPercent(BatteryType.NiZn),
            getCalculatedPercent(BatteryType.NiMH))
    }

    enum class BatteryStatusType {
        Normal,
        Low,
        Unknown
    }
}
