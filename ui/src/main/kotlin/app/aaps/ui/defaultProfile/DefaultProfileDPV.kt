package app.aaps.ui.defaultProfile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.pureProfileFromJson
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultProfileDPV @Inject constructor(
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil
) {

    private var oneToFive = arrayOf(3.97, 3.61, 3.46, 3.70, 3.76, 3.87, 4.18, 4.01, 3.76, 3.54, 3.15, 2.80, 2.86, 3.21, 3.61, 3.97, 4.43, 4.96, 5.10, 5.50, 5.81, 6.14, 5.52, 5.10)
    private var sixToEleven = arrayOf(4.20, 4.27, 4.41, 4.62, 4.92, 5.09, 5.01, 4.47, 3.89, 3.33, 3.10, 2.91, 2.97, 3.08, 3.36, 3.93, 4.52, 4.76, 4.69, 4.63, 4.63, 4.47, 4.47, 4.31)
    private var twelveToEighteen = arrayOf(3.47, 3.80, 4.31, 4.95, 5.59, 6.11, 5.89, 5.11, 4.31, 3.78, 3.55, 3.39, 3.35, 3.39, 3.64, 3.97, 4.53, 4.59, 4.50, 4.00, 3.69, 3.39, 3.35, 3.35)

    fun profile(age: Int, tdd: Double, basalSumPct: Double, units: GlucoseUnit): PureProfile? {
        val basalSum = tdd * basalSumPct
        val profile = JSONObject()
        if (age in 1..5) {
            profile.put("basal", arrayToJson(oneToFive, basalSum))
            profile.put("carbratio", singleValueArray(0.0))
            profile.put("sens", singleValueArrayFromMmolToUnits(0.0, units))
        } else if (age in 6..11) {
            profile.put("basal", arrayToJson(sixToEleven, basalSum))
            profile.put("carbratio", singleValueArray(0.0))
            profile.put("sens", singleValueArrayFromMmolToUnits(0.0, units))
        } else if (age in 12..18) {
            profile.put("basal", arrayToJson(twelveToEighteen, basalSum))
            profile.put("carbratio", singleValueArray(0.0))
            profile.put("sens", singleValueArrayFromMmolToUnits(0.0, units))
        } else if (age > 18) {
            return null
        }
        profile.put("dia", 5.0)
        profile.put("carbs_hr", 20) // not used
        profile.put("delay", 5.0) // not used
        profile.put("timezone", TimeZone.getDefault().id)
        profile.put("target_high", JSONArray().put(JSONObject().put("time", "00:00").put("value", profileUtil.fromMgdlToUnits(108.0, units))))
        profile.put("target_low", JSONArray().put(JSONObject().put("time", "00:00").put("value", profileUtil.fromMgdlToUnits(108.0, units))))
        profile.put("units", units.asText)
        return pureProfileFromJson(profile, dateUtil)
    }

    private fun arrayToJson(b: Array<Double>, basalSum: Double): JSONArray {
        val basals = JSONArray()
        for (i in 0..23) {
            val time = String.format(Locale.ENGLISH, "%02d:00", i)
            basals.put(JSONObject().put("time", time).put("value", (b[i] * basalSum / 100.0).toString()).put("timeAsSeconds", i * 3600))
        }
        return basals
    }

    @Suppress("SameParameterValue")
    private fun singleValueArray(value: Double): JSONArray {
        val array = JSONArray()
        array.put(JSONObject().put("time", "00:00").put("value", value).put("timeAsSeconds", 0 * 3600))
        return array
    }

    @Suppress("SameParameterValue")
    private fun singleValueArrayFromMmolToUnits(value: Double, units: GlucoseUnit): JSONArray {
        val array = JSONArray()
        array.put(JSONObject().put("time", "00:00").put("value", profileUtil.fromMmolToUnits(value, units)).put("timeAsSeconds", 0 * 3600))
        return array
    }
}