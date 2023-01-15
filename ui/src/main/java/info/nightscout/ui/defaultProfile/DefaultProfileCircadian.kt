package info.nightscout.ui.defaultProfile

import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.PureProfile
import info.nightscout.interfaces.utils.Round
import info.nightscout.shared.utils.DateUtil
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Suppress("LocalVariableName")
@Singleton
class DefaultProfileCircadian @Inject constructor(val dateUtil: DateUtil) {

    // Factors (Gary Scheiner, Think like a pancreas, Da Capo Lifelong Books ISBN 978-0738215143)
    private var oneToTen = arrayOf(1.12, 1.12, 1.11, 1.11, 1.11, 1.09, 1.09, 1.12, 1.11, 0.98, 0.90, 0.84, 0.81, 0.81, 0.86, 0.86, 0.88, 0.90, 0.93, 0.97, 1.05, 1.07, 1.09, 1.07)
    private var elevenToTwenty = arrayOf(1.09, 1.11, 1.09, 1.09, 1.09, 1.09, 1.11, 1.11, 1.09, 1.04, 0.96, 0.88, 0.84, 0.84, 0.85, 0.86, 0.91, 0.92, 0.92, 0.94, 0.99, 1.04, 1.05, 1.06)
    private var twentyToSixty = arrayOf(1.01, 1.04, 1.12, 1.15, 1.20, 1.26, 1.21, 1.20, 1.15, 1.06, 0.98, 0.88, 0.86, 0.85, 0.85, 0.83, 0.83, 0.86, 0.89, 0.89, 0.94, 0.97, 0.98, 0.98)
    private var sixtyOneAndHigher = arrayOf(0.95, 1.02, 1.07, 1.14, 1.19, 1.38, 1.43, 1.36, 1.24, 1.24, 1.14, 1.00, 0.95, 0.93, 0.90, 0.79, 0.79, 0.79, 0.76, 0.74, 0.74, 0.79, 0.79, 0.88)

    fun profile(age: Int, tdd: Double, basalSumPct: Double, isf: Double, ic: Double, timeshift: Double, units: GlucoseUnit): PureProfile? {
        val avgBasal = (tdd * basalSumPct) / 24
        val profile = JSONObject()

        if (age in 1..10) {
            profile.put("basal", arrayToJson(oneToTen, avgBasal, timeshift))
            profile.put("carbratio", arrayToJsonDiv(oneToTen, ic, timeshift + 1))
            profile.put("sens", arrayToJsonDiv(oneToTen, isf, timeshift + 1))
        } else if (age in 11..20) {
            profile.put("basal", arrayToJson(elevenToTwenty, avgBasal, timeshift))
            profile.put("carbratio", arrayToJsonDiv(elevenToTwenty, ic, timeshift + 1))
            profile.put("sens", arrayToJsonDiv(elevenToTwenty, isf, timeshift + 1))
        } else if (age in 21..60) {
            profile.put("basal", arrayToJson(twentyToSixty, avgBasal, timeshift))
            profile.put("carbratio", arrayToJsonDiv(twentyToSixty, ic, timeshift + 1))
            profile.put("sens", arrayToJsonDiv(twentyToSixty, isf, timeshift + 1))
        } else if (age > 60) {
            profile.put("basal", arrayToJson(sixtyOneAndHigher, avgBasal, timeshift))
            profile.put("carbratio", arrayToJsonDiv(sixtyOneAndHigher, ic, timeshift + 1))
            profile.put("sens", arrayToJsonDiv(sixtyOneAndHigher, isf, timeshift + 1))
        }
        profile.put("dia", 5.0)
        profile.put("carbs_hr", 20) // not used
        profile.put("delay", 5.0) // not used
        profile.put("timezone", TimeZone.getDefault().id)
        profile.put("target_high", JSONArray().put(JSONObject().put("time", "00:00").put("value", Profile.fromMgdlToUnits(108.0, units))))
        profile.put("target_low", JSONArray().put(JSONObject().put("time", "00:00").put("value", Profile.fromMgdlToUnits(108.0, units))))
        profile.put("units", units.asText)
        return pureProfileFromJson(profile, dateUtil)
    }

    private fun arrayToJson(factors: Array<Double>, average: Double, timeshift: Double): JSONArray {
        val ret = JSONArray()
        for (i in 0..23) {
            val time = String.format(Locale.ENGLISH, "%02d:00", i)
            val index = (i + abs(timeshift.toInt() - 24)) % 24
            val value = average * factors[index]
            ret.put(JSONObject().put("time", time).put("value", value.toString()).put("timeAsSeconds", i * 3600))
        }
        return ret
    }

    private fun arrayToJsonDiv(factors: Array<Double>, average: Double, timeshift: Double): JSONArray {
        val ret = JSONArray()
        for (i in 0..23) {
            val time = String.format(Locale.ENGLISH, "%02d:00", i)
            val index = (i + abs(timeshift.toInt() - 24)) % 24
            val value = average / factors[index]
            ret.put(JSONObject().put("time", time).put("value", value.toString()).put("timeAsSeconds", i * 3600))
        }
        return ret
    }
}