package app.aaps.wear.testing.mockers

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.SingleBg
import app.aaps.core.interfaces.utils.SafeParse.stringToDouble
import app.aaps.wear.WearTestBase
import app.aaps.wear.data.RawDisplayData
import kotlin.String

class RawDataMocker {

    fun rawSgv(sgv: String?, m: Int, deltaString: String): RawDisplayData {
        val raw = RawDisplayData()
        val delta = stringToDouble(deltaString)
        val d: String =
            when {
                delta <= -3.5 * 5 -> "\u21ca"
                delta <= -2 * 5   -> "\u2193"
                delta <= -1 * 5   -> "\u2198"
                delta <= 1 * 5    -> "\u2192"
                delta <= 2 * 5    -> "\u2197"
                delta <= 3.5 * 5  -> "\u2191"
                else              -> "\u21c8"
            }

        raw.singleBg = arrayOf<SingleBg>(
            SingleBg(
                0,
                WearTestBase.backInTime(0, 0, m, 0),
                sgv!!,
                "",
                d,
                deltaString,
                deltaString,
                "",
                "",
                0,
                0.0,
                0.0,
                0.0,
                0
            )
        )
        return raw
    }

    fun rawDelta(m: Int, delta: String): RawDisplayData {
        val raw = RawDisplayData()
        raw.singleBg = arrayOf<SingleBg>(
            SingleBg(
                0,
                WearTestBase.backInTime(0, 0, m, 0),
                "",
                "",
                "",
                delta,
                delta,
                "",
                "",
                0,
                0.0,
                0.0,
                0.0,
                0
            )
        )
        return raw
    }

    fun rawCobIobBr(cob: String, iob: String, br: String): RawDisplayData {
        val raw = RawDisplayData()
        raw.status = arrayOf<EventData.Status>(
            EventData.Status(
                dataset = 0,
                externalStatus = "",
                iobSum = iob,
                iobDetail = "",
                cob = cob,
                currentBasal = br,
                battery = "",
                rigBattery = "",
                openApsStatus = 0L,
                bgi = "",
                batteryLevel = 0,
                patientName = "",
                tempTarget = "",
                tempTargetLevel = 0,
                reservoirString = "",
                reservoir = 0.0,
                reservoirLevel = 0
            )
        )
        return raw
    }

    fun rawIob(iob: String, iob2: String): RawDisplayData {
        val raw = RawDisplayData()
        raw.status = arrayOf<EventData.Status>(
            EventData.Status(
                dataset = 0,
                externalStatus = "",
                iobSum = iob,
                iobDetail = iob2,
                cob = "",
                currentBasal = "",
                battery = "",
                rigBattery = "",
                openApsStatus = 0L,
                bgi = "",
                batteryLevel = 0,
                patientName = "",
                tempTarget = "",
                tempTargetLevel = 0,
                reservoirString = "",
                reservoir = 0.0,
                reservoirLevel = 0
            )
        )
        return raw
    }

    fun rawCob(cob: String?): RawDisplayData {
        val raw = RawDisplayData()
        raw.status = arrayOf<EventData.Status>(
            EventData.Status(
                dataset = 0,
                externalStatus = "",
                iobSum = "",
                iobDetail = "",
                cob = cob!!,
                currentBasal = "",
                battery = "",
                rigBattery = "",
                openApsStatus = 0L,
                bgi = "",
                batteryLevel = 0,
                patientName = "",
                tempTarget = "",
                tempTargetLevel = 0,
                reservoirString = "",
                reservoir = 0.0,
                reservoirLevel = 0
            )
        )
        return raw
    }
}
