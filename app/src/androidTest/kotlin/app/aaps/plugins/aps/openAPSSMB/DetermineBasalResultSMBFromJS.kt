package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.openAPS.APSResultObject
import dagger.android.HasAndroidInjector
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class DetermineBasalResultSMBFromJS private constructor(injector: HasAndroidInjector) : APSResultObject(injector) {

    @Inject lateinit var dateUtil: DateUtil

    private var json: JSONObject? = null

    private var eventualBG = 0.0
    private var snoozeBG = 0.0
    override var variableSens: Double? = null

    internal constructor(injector: HasAndroidInjector, result: JSONObject) : this(injector) {
        date = dateUtil.now()
        json = result
        try {
            if (result.has("error")) {
                reason = result.getString("error")
                return
            }
            reason = result.getString("reason")
            if (result.has("eventualBG")) eventualBG = result.getDouble("eventualBG")
            if (result.has("snoozeBG")) snoozeBG = result.getDouble("snoozeBG")
            //if (result.has("insulinReq")) insulinReq = result.getDouble("insulinReq");
            if (result.has("carbsReq")) carbsReq = result.getInt("carbsReq")
            if (result.has("carbsReqWithin")) carbsReqWithin = result.getInt("carbsReqWithin")
            if (result.has("rate") && result.has("duration")) {
                isTempBasalRequested = true
                rate = result.getDouble("rate")
                if (rate < 0.0) rate = 0.0
                duration = result.getInt("duration")
            } else {
                rate = (-1).toDouble()
                duration = -1
            }
            if (result.has("units")) {
                smb = result.getDouble("units")
            } else {
                smb = 0.0
            }
            if (result.has("targetBG")) {
                targetBG = result.getDouble("targetBG")
            }
            if (result.has("deliverAt")) {
                val date = result.getString("deliverAt")
                try {
                    deliverAt = dateUtil.fromISODateString(date)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.APS, "Error parsing 'deliverAt' date: $date", e)
                }
            }
            if (result.has("variable_sens")) variableSens = result.getDouble("variable_sens")
        } catch (e: JSONException) {
            aapsLogger.error(LTag.APS, "Error parsing determine-basal result JSON", e)
        }
    }

    override fun newAndClone(): DetermineBasalResultSMBFromJS {
        val newResult = DetermineBasalResultSMBFromJS(injector)
        doClone(newResult)
        newResult.eventualBG = eventualBG
        newResult.snoozeBG = snoozeBG
        newResult.json = json
        return newResult
    }

    override fun json(): JSONObject? = json

    override fun predictions(): Predictions {
        val predictions = Predictions()

        json?.let { json ->
            if (json.has("predBGs")) {
                val predBGs = json.getJSONObject("predBGs")
                if (predBGs.has("IOB")) {
                    val iob = predBGs.getJSONArray("IOB")
                    val list = mutableListOf<Int>()
                    for (i in 1 until iob.length()) list.add(iob.getInt(i))
                    predictions.IOB = list
                }
                if (predBGs.has("aCOB")) {
                    val iob = predBGs.getJSONArray("aCOB")
                    val list = mutableListOf<Int>()
                    for (i in 1 until iob.length()) list.add(iob.getInt(i))
                    predictions.aCOB = list
                }
                if (predBGs.has("COB")) {
                    val iob = predBGs.getJSONArray("COB")
                    val list = mutableListOf<Int>()
                    for (i in 1 until iob.length()) list.add(iob.getInt(i))
                    predictions.COB = list
                }
                if (predBGs.has("UAM")) {
                    val iob = predBGs.getJSONArray("UAM")
                    val list = mutableListOf<Int>()
                    for (i in 1 until iob.length()) list.add(iob.getInt(i))
                    predictions.UAM = list
                }
                if (predBGs.has("ZT")) {
                    val iob = predBGs.getJSONArray("ZT")
                    val list = mutableListOf<Int>()
                    for (i in 1 until iob.length()) list.add(iob.getInt(i))
                    predictions.ZT = list
                }
            }
        }
        return predictions
    }

    init {
        hasPredictions = true
    }
}