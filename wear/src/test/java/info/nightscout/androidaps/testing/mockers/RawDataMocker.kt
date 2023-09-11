package info.nightscout.androidaps.testing.mockers

import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.WearTestBase
import info.nightscout.shared.SafeParse.stringToDouble
import info.nightscout.rx.weardata.EventData
import info.nightscout.rx.weardata.EventData.SingleBg

class RawDataMocker() {

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

        raw.singleBg = SingleBg(
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
        return raw
    }

    fun rawDelta(m: Int, delta: String): RawDisplayData {
        val raw = RawDisplayData()
        raw.singleBg = SingleBg(
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
        return raw
    }

    fun rawCobIobBr(cob: String, iob: String, br: String): RawDisplayData {
        val raw = RawDisplayData()
        raw.status = EventData.Status(
            "",
            iob,
            "",
            cob,
            br,
            "",
            "",
            0L,
            "",
            0
        )
        return raw
    }

    fun rawIob(iob: String, iob2: String): RawDisplayData {
        val raw = RawDisplayData()
        raw.status = EventData.Status(
            "",
            iob,
            iob2,
            "",
            "",
            "",
            "",
            0L,
            "",
            0
        )
        return raw
    }

    fun rawCob(cob: String?): RawDisplayData {
        val raw = RawDisplayData()
        raw.status = EventData.Status(
            "",
            "",
            "",
            cob!!,
            "",
            "",
            "",
            0L,
            "",
            0
        )
        return raw
    }

}
