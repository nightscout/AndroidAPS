package app.aaps.plugins.aps.openAPSAMA

import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.openAPS.APSResultObject
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.mozilla.javascript.NativeObject
import javax.inject.Inject

class DetermineBasalResultAMAFromJS @Inject constructor(injector: HasAndroidInjector) : APSResultObject(injector) {

    @Inject lateinit var dateUtil: DateUtil

    private var json: JSONObject? = null

    private var eventualBG = 0.0
    private var snoozeBG = 0.0

    internal constructor(injector: HasAndroidInjector, result: NativeObject, j: JSONObject) : this(injector) {
        date = dateUtil.now()
        json = j
        if (result.containsKey("error")) {
            reason = result["error"].toString()
            isTempBasalRequested = false
            rate = (-1).toDouble()
            duration = -1
        } else {
            reason = result["reason"].toString()
            if (result.containsKey("eventualBG")) eventualBG = result["eventualBG"] as Double
            if (result.containsKey("snoozeBG")) snoozeBG = result["snoozeBG"] as Double
            if (result.containsKey("rate")) {
                rate = result["rate"] as Double
                if (rate < 0.0) rate = 0.0
                isTempBasalRequested = true
            } else {
                rate = (-1).toDouble()
                isTempBasalRequested = false
            }
            if (result.containsKey("duration")) {
                duration = (result["duration"] as Double).toInt()
                //changeRequested as above
            } else {
                duration = -1
                isTempBasalRequested = false
            }
        }
    }

    override fun newAndClone(): DetermineBasalResultAMAFromJS {
        val newResult = DetermineBasalResultAMAFromJS(injector)
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