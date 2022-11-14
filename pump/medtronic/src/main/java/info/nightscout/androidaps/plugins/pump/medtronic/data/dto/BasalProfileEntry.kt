package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import org.joda.time.LocalTime
import java.util.Locale

/**
 * Created by geoff on 6/1/15.
 * This is a helper class for BasalProfile, only used for interpreting the contents of BasalProfile
 * - fixed rate is not one bit but two
 */
class BasalProfileEntry {

    var rate_raw: ByteArray
    var rate = 0.0

    var startTime_raw: Byte
    var startTime : LocalTime? = null // Just a "time of day"

    constructor() {
        rate = -9.999E6
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(0xFF, true)
        startTime = LocalTime(0)
        startTime_raw = 0xFF.toByte()
    }

    constructor(rate: Double, hour: Int, minutes: Int) {
        val data = MedtronicUtil.getBasalStrokes(rate, true)
        rate_raw = ByteArray(2)
        rate_raw[0] = data[1]
        rate_raw[1] = data[0]
        var interval = hour * 2
        if (minutes == 30) {
            interval++
        }
        startTime_raw = interval.toByte()
        startTime = LocalTime(hour, if (minutes == 30) 30 else 0)
    }

    internal constructor(aapsLogger: AAPSLogger, rateStrokes: Int, startTimeInterval: Int) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(rateStrokes, true)
        rate = rateStrokes * 0.025
        startTime_raw = startTimeInterval.toByte()
        startTime = try {
            LocalTime(startTimeInterval / 2, startTimeInterval % 2 * 30)
        } catch (ex: Exception) {
            aapsLogger.error(
                LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error creating BasalProfileEntry: startTimeInterval=%d, startTime_raw=%d, hours=%d, rateStrokes=%d",
                                             startTimeInterval, startTime_raw, startTimeInterval / 2, rateStrokes))
            throw ex
        }
    }

    internal constructor(rateByte: Byte, startTimeByte: Int) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(rateByte.toInt(), true)
        rate = rateByte * 0.025
        startTime_raw = startTimeByte.toByte()
        startTime = LocalTime(startTimeByte / 2, startTimeByte % 2 * 30)
    }

}
