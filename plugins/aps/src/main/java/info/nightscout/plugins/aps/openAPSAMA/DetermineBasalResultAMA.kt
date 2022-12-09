package info.nightscout.plugins.aps.openAPSAMA

import dagger.android.HasAndroidInjector
import info.nightscout.plugins.aps.APSResultObject
import info.nightscout.rx.logging.LTag
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.NativeObject

class DetermineBasalResultAMA private constructor(injector: HasAndroidInjector) : APSResultObject(injector) {

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

    override fun newAndClone(injector: HasAndroidInjector): DetermineBasalResultAMA {
        val newResult = DetermineBasalResultAMA(injector)
        doClone(newResult)
        newResult.eventualBG = eventualBG
        newResult.snoozeBG = snoozeBG
        return newResult
    }

    override fun json(): JSONObject? {
        try {
            return JSONObject(json.toString())
        } catch (e: JSONException) {
            aapsLogger.error(LTag.APS, "Unhandled exception", e)
        }
        return null
    }

    init {
        hasPredictions = true
    }
}