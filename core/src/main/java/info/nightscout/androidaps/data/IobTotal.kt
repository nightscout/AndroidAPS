package info.nightscout.androidaps.data

import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.Round
import org.json.JSONException
import org.json.JSONObject

@Suppress("SpellCheckingInspection")
class IobTotal(var time: Long) : DataPointWithLabelInterface {

    @JvmField var iob = 0.0
    @JvmField var activity = 0.0
    @JvmField var bolussnooze = 0.0
    @JvmField var basaliob = 0.0
    @JvmField var netbasalinsulin = 0.0
    @JvmField var hightempinsulin = 0.0

    // oref1
    @JvmField var lastBolusTime: Long = 0
    var iobWithZeroTemp: IobTotal? = null
    @JvmField var netInsulin = 0.0 // for calculations from temp basals only
    @JvmField var extendedBolusInsulin = 0.0 // total insulin for extended bolus
    fun copy(): IobTotal {
        val i = IobTotal(time)
        i.iob = iob
        i.activity = activity
        i.bolussnooze = bolussnooze
        i.basaliob = basaliob
        i.netbasalinsulin = netbasalinsulin
        i.hightempinsulin = hightempinsulin
        i.lastBolusTime = lastBolusTime
        i.iobWithZeroTemp = iobWithZeroTemp?.copy()
        i.netInsulin = netInsulin
        i.extendedBolusInsulin = extendedBolusInsulin
        return i
    }

    operator fun plus(other: IobTotal): IobTotal {
        iob += other.iob
        activity += other.activity
        bolussnooze += other.bolussnooze
        basaliob += other.basaliob
        netbasalinsulin += other.netbasalinsulin
        hightempinsulin += other.hightempinsulin
        netInsulin += other.netInsulin
        extendedBolusInsulin += other.extendedBolusInsulin
        return this
    }

    fun round(): IobTotal {
        iob = Round.roundTo(iob, 0.001)
        activity = Round.roundTo(activity, 0.0001)
        bolussnooze = Round.roundTo(bolussnooze, 0.0001)
        basaliob = Round.roundTo(basaliob, 0.001)
        netbasalinsulin = Round.roundTo(netbasalinsulin, 0.001)
        hightempinsulin = Round.roundTo(hightempinsulin, 0.001)
        netInsulin = Round.roundTo(netInsulin, 0.001)
        extendedBolusInsulin = Round.roundTo(extendedBolusInsulin, 0.001)
        return this
    }

    fun json(dateUtil: DateUtil): JSONObject {
        val json = JSONObject()
        try {
            json.put("iob", iob)
            json.put("basaliob", basaliob)
            json.put("activity", activity)
            json.put("time", dateUtil.toISOString(time))
        } catch (ignored: JSONException) {
        }
        return json
    }

    fun determineBasalJson(dateUtil: DateUtil): JSONObject {
        val json = JSONObject()
        try {
            json.put("iob", iob)
            json.put("basaliob", basaliob)
            json.put("bolussnooze", bolussnooze)
            json.put("activity", activity)
            json.put("lastBolusTime", lastBolusTime)
            json.put("time", dateUtil.toISOString(time))
            /*

            This is requested by SMB determine_basal but by based on Scott's info
            it's MDT specific safety check only
            It's causing rounding issues in determine_basal

            JSONObject lastTemp = new JSONObject();
            lastTemp.put("date", lastTempDate);
            lastTemp.put("rate", lastTempRate);
            lastTemp.put("duration", lastTempDuration);
            json.put("lastTemp", lastTemp);
            */
            if (iobWithZeroTemp != null) {
                val iwzt = iobWithZeroTemp!!.determineBasalJson(dateUtil)
                json.put("iobWithZeroTemp", iwzt)
            }
        } catch (ignored: JSONException) {
        }
        return json
    }

    // DataPoint interface
    private var color = 0
    override fun getX(): Double {
        return time.toDouble()
    }

    override fun getY(): Double {
        return iob
    }

    override fun setY(y: Double) {}

    override fun getLabel(): String {
        return ""
    }

    override fun getDuration(): Long {
        return 0
    }

    override fun getShape(): PointsWithLabelGraphSeries.Shape {
        return PointsWithLabelGraphSeries.Shape.IOBPREDICTION
    }

    override fun getSize(): Float {
        return 0.5f
    }

    override fun getColor(): Int {
        return color
    }

    fun setColor(color: Int): IobTotal {
        this.color = color
        return this
    }

    companion object {

        fun combine(bolusIOB: IobTotal, basalIob: IobTotal): IobTotal {
            val result = IobTotal(bolusIOB.time)
            result.iob = bolusIOB.iob + basalIob.basaliob
            result.activity = bolusIOB.activity + basalIob.activity
            result.bolussnooze = bolusIOB.bolussnooze
            result.basaliob = bolusIOB.basaliob + basalIob.basaliob
            result.netbasalinsulin = bolusIOB.netbasalinsulin + basalIob.netbasalinsulin
            result.hightempinsulin = basalIob.hightempinsulin + bolusIOB.hightempinsulin
            result.netInsulin = basalIob.netInsulin + bolusIOB.netInsulin
            result.extendedBolusInsulin = basalIob.extendedBolusInsulin + bolusIOB.extendedBolusInsulin
            result.lastBolusTime = bolusIOB.lastBolusTime
            result.iobWithZeroTemp = basalIob.iobWithZeroTemp
            return result
        }
    }
}