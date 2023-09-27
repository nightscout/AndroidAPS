package app.aaps.ui.defaultProfile

import app.aaps.core.main.extensions.pureProfileFromJson
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
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
class DefaultProfile @Inject constructor(
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil
) {

    private var oneToFive: TreeMap<Double, Array<Double>> = TreeMap()
    private var sixToEleven: TreeMap<Double, Array<Double>> = TreeMap()
    private var twelveToSeventeen: TreeMap<Double, Array<Double>> = TreeMap()
    @Suppress("unused") var eighteenToTwentyFour: TreeMap<Double, Array<Double>> = TreeMap()

    fun profile(age: Int, tdd: Double, weight: Double, units: GlucoseUnit): PureProfile? {
        val profile = JSONObject()
        if (age in 1..5) {
            val _tdd = if (tdd == 0.0) 0.6 * weight else tdd
            closest(oneToFive, _tdd * 0.3)?.let { array -> profile.put("basal", arrayToJson(array)) }
            val ic = Round.roundTo(250.0 / _tdd, 1.0)
            profile.put("carbratio", singleValueArray(ic, arrayOf(0.0, -4.0, -1.0, -2.0, -4.0, 0.0, -4.0)))
            val isf = Round.roundTo(200.0 / _tdd, 0.1)
            profile.put("sens", singleValueArrayFromMmolToUnits(isf, arrayOf(0.0, -2.0, -0.0, -0.0, -2.0, 0.0, -2.0), units))
        } else if (age in 6..11) {
            val _tdd = if (tdd == 0.0) 0.8 * weight else tdd
            closest(sixToEleven, _tdd * 0.4)?.let { array -> profile.put("basal", arrayToJson(array)) }
            val ic = Round.roundTo(375.0 / _tdd, 1.0)
            profile.put("carbratio", singleValueArray(ic, arrayOf(0.0, -3.0, 0.0, -1.0, -3.0, 0.0, -2.0)))
            val isf = Round.roundTo(170.0 / _tdd, 0.1)
            profile.put("sens", singleValueArrayFromMmolToUnits(isf, arrayOf(0.0, -1.0, -0.0, -0.0, -1.0, 0.0, -1.0), units))
        } else if (age in 12..18) {
            val _tdd = if (tdd == 0.0) 1.0 * weight else tdd
            closest(twelveToSeventeen, _tdd * 0.5)?.let { array -> profile.put("basal", arrayToJson(array)) }
            val ic = Round.roundTo(500.0 / _tdd, 1.0)
            profile.put("carbratio", singleValueArray(ic, arrayOf(0.0, -1.0, 0.0, 0.0, -1.0, 0.0, -1.0)))
            val isf = Round.roundTo(100.0 / _tdd, 0.1)
            profile.put("sens", singleValueArrayFromMmolToUnits(isf, arrayOf(0.2, 0.0, 0.2, 0.2, 0.0, 0.2, 0.2), units))
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

    init {
        oneToFive[1.00] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050)
        oneToFive[1.13] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050)
        oneToFive[1.25] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.050, 0.050, 0.050, 0.075, 0.075, 0.100, 0.050, 0.050)
        oneToFive[1.38] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.050, 0.050, 0.050, 0.075, 0.075, 0.100, 0.050, 0.050)
        oneToFive[1.50] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.075, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.050, 0.050, 0.050, 0.075, 0.100, 0.100, 0.050, 0.050)
        oneToFive[1.75] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.100, 0.050, 0.050, 0.050, 0.060, 0.060, 0.075, 0.075, 0.050, 0.050, 0.050, 0.100, 0.125, 0.100, 0.050, 0.050)
        oneToFive[2.00] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.100, 0.075, 0.050, 0.050, 0.065, 0.065, 0.075, 0.075, 0.050, 0.050, 0.050, 0.100, 0.125, 0.100, 0.050, 0.050)
        oneToFive[2.25] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.100, 0.100, 0.100, 0.075, 0.060, 0.060, 0.070, 0.070, 0.100, 0.100, 0.050, 0.050, 0.050, 0.125, 0.150, 0.125, 0.065, 0.050)
        oneToFive[2.50] =
            arrayOf(0.050, 0.050, 0.050, 0.050, 0.075, 0.075, 0.100, 0.125, 0.125, 0.100, 0.065, 0.065, 0.075, 0.075, 0.125, 0.125, 0.060, 0.060, 0.060, 0.150, 0.150, 0.150, 0.070, 0.060)
        oneToFive[2.75] =
            arrayOf(0.075, 0.075, 0.075, 0.100, 0.100, 0.100, 0.125, 0.150, 0.125, 0.100, 0.070, 0.070, 0.080, 0.080, 0.150, 0.150, 0.070, 0.070, 0.070, 0.175, 0.175, 0.175, 0.080, 0.070)
        oneToFive[3.25] =
            arrayOf(0.100, 0.100, 0.100, 0.125, 0.125, 0.125, 0.150, 0.150, 0.150, 0.100, 0.080, 0.080, 0.100, 0.100, 0.175, 0.175, 0.075, 0.075, 0.075, 0.200, 0.200, 0.200, 0.090, 0.080)
        oneToFive[3.75] =
            arrayOf(0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.175, 0.175, 0.175, 0.100, 0.085, 0.085, 0.110, 0.110, 0.185, 0.185, 0.080, 0.080, 0.080, 0.225, 0.225, 0.225, 0.100, 0.090)
        oneToFive[4.25] =
            arrayOf(0.125, 0.125, 0.130, 0.140, 0.140, 0.140, 0.200, 0.200, 0.200, 0.125, 0.090, 0.090, 0.120, 0.120, 0.200, 0.200, 0.100, 0.100, 0.100, 0.250, 0.250, 0.250, 0.125, 0.100)
        oneToFive[4.75] =
            arrayOf(0.125, 0.130, 0.135, 0.150, 0.150, 0.150, 0.200, 0.225, 0.200, 0.125, 0.100, 0.100, 0.125, 0.125, 0.250, 0.200, 0.110, 0.125, 0.125, 0.275, 0.275, 0.275, 0.130, 0.125)
        oneToFive[5.25] =
            arrayOf(0.150, 0.150, 0.150, 0.170, 0.170, 0.170, 0.225, 0.225, 0.225, 0.130, 0.125, 0.125, 0.140, 0.140, 0.250, 0.250, 0.150, 0.150, 0.150, 0.300, 0.300, 0.300, 0.150, 0.150)
        oneToFive[6.00] =
            arrayOf(0.170, 0.170, 0.175, 0.200, 0.200, 0.200, 0.250, 0.250, 0.250, 0.150, 0.125, 0.125, 0.150, 0.150, 0.275, 0.275, 0.170, 0.150, 0.150, 0.350, 0.350, 0.350, 0.175, 0.150)
        oneToFive[6.75] =
            arrayOf(0.200, 0.200, 0.200, 0.225, 0.225, 0.225, 0.275, 0.275, 0.275, 0.200, 0.130, 0.130, 0.175, 0.175, 0.300, 0.300, 0.170, 0.175, 0.175, 0.375, 0.375, 0.375, 0.200, 0.175)
        oneToFive[7.50] =
            arrayOf(0.225, 0.230, 0.235, 0.250, 0.250, 0.250, 0.300, 0.300, 0.300, 0.250, 0.150, 0.150, 0.200, 0.200, 0.325, 0.325, 0.200, 0.200, 0.200, 0.400, 0.450, 0.400, 0.350, 0.200)

        sixToEleven[5.26] = arrayOf(0.18, 0.18, 0.18, 0.20, 0.20, 0.23, 0.25, 0.25, 0.25, 0.18, 0.15, 0.13, 0.15, 0.15, 0.25, 0.25, 0.20, 0.15, 0.18, 0.25, 0.25, 0.25, 0.23, 0.20)
        sixToEleven[5.61] = arrayOf(0.18, 0.20, 0.20, 0.23, 0.23, 0.25, 0.28, 0.28, 0.25, 0.20, 0.15, 0.13, 0.15, 0.18, 0.25, 0.25, 0.20, 0.15, 0.18, 0.28, 0.25, 0.25, 0.23, 0.20)
        sixToEleven[5.93] = arrayOf(0.20, 0.20, 0.23, 0.25, 0.25, 0.25, 0.30, 0.30, 0.30, 0.25, 0.15, 0.15, 0.18, 0.18, 0.28, 0.28, 0.20, 0.20, 0.20, 0.28, 0.28, 0.28, 0.25, 0.23)
        sixToEleven[6.26] = arrayOf(0.20, 0.23, 0.23, 0.25, 0.25, 0.28, 0.33, 0.30, 0.30, 0.25, 0.18, 0.15, 0.18, 0.18, 0.28, 0.28, 0.23, 0.20, 0.20, 0.28, 0.30, 0.28, 0.25, 0.23)
        sixToEleven[6.60] = arrayOf(0.23, 0.23, 0.25, 0.25, 0.25, 0.28, 0.33, 0.33, 0.33, 0.28, 0.18, 0.15, 0.18, 0.18, 0.30, 0.28, 0.23, 0.23, 0.20, 0.30, 0.30, 0.30, 0.25, 0.25)
        sixToEleven[7.26] = arrayOf(0.23, 0.25, 0.28, 0.28, 0.30, 0.33, 0.38, 0.35, 0.35, 0.30, 0.18, 0.18, 0.18, 0.19, 0.33, 0.30, 0.25, 0.23, 0.23, 0.33, 0.33, 0.33, 0.30, 0.25)
        sixToEleven[7.92] = arrayOf(0.25, 0.25, 0.28, 0.30, 0.33, 0.35, 0.38, 0.38, 0.38, 0.35, 0.20, 0.20, 0.23, 0.25, 0.35, 0.33, 0.30, 0.25, 0.25, 0.35, 0.35, 0.35, 0.33, 0.28)
        sixToEleven[8.57] = arrayOf(0.28, 0.28, 0.30, 0.30, 0.33, 0.38, 0.40, 0.43, 0.40, 0.38, 0.25, 0.25, 0.25, 0.28, 0.38, 0.38, 0.33, 0.28, 0.28, 0.38, 0.40, 0.38, 0.35, 0.30)
        sixToEleven[9.24] = arrayOf(0.30, 0.33, 0.35, 0.38, 0.40, 0.40, 0.43, 0.45, 0.43, 0.40, 0.30, 0.25, 0.28, 0.28, 0.38, 0.40, 0.33, 0.30, 0.30, 0.40, 0.43, 0.40, 0.38, 0.35)
        sixToEleven[9.89] = arrayOf(0.35, 0.35, 0.38, 0.40, 0.40, 0.43, 0.45, 0.48, 0.45, 0.40, 0.30, 0.25, 0.28, 0.30, 0.40, 0.43, 0.35, 0.33, 0.33, 0.43, 0.45, 0.43, 0.40, 0.38)
        sixToEleven[10.56] = arrayOf(0.38, 0.38, 0.40, 0.43, 0.45, 0.45, 0.48, 0.50, 0.48, 0.40, 0.35, 0.25, 0.30, 0.33, 0.43, 0.45, 0.35, 0.35, 0.35, 0.45, 0.48, 0.45, 0.43, 0.40)
        sixToEleven[11.21] = arrayOf(0.40, 0.43, 0.43, 0.45, 0.48, 0.50, 0.53, 0.55, 0.50, 0.40, 0.35, 0.30, 0.33, 0.33, 0.45, 0.48, 0.38, 0.35, 0.37, 0.50, 0.50, 0.48, 0.45, 0.43)
        sixToEleven[11.88] = arrayOf(0.43, 0.43, 0.45, 0.45, 0.48, 0.50, 0.55, 0.58, 0.50, 0.40, 0.35, 0.33, 0.33, 0.33, 0.48, 0.50, 0.40, 0.38, 0.38, 0.53, 0.53, 0.50, 0.48, 0.45)
        sixToEleven[12.53] = arrayOf(0.45, 0.45, 0.48, 0.50, 0.53, 0.55, 0.60, 0.60, 0.60, 0.45, 0.40, 0.35, 0.35, 0.38, 0.50, 0.53, 0.40, 0.38, 0.38, 0.55, 0.58, 0.55, 0.50, 0.48)
        sixToEleven[13.19] = arrayOf(0.48, 0.48, 0.50, 0.55, 0.58, 0.60, 0.65, 0.65, 0.65, 0.50, 0.45, 0.36, 0.38, 0.40, 0.55, 0.55, 0.45, 0.40, 0.40, 0.60, 0.60, 0.58, 0.55, 0.50)
        sixToEleven[14.18] = arrayOf(0.53, 0.53, 0.55, 0.60, 0.65, 0.68, 0.70, 0.70, 0.68, 0.60, 0.55, 0.40, 0.40, 0.45, 0.60, 0.60, 0.50, 0.45, 0.45, 0.63, 0.65, 0.63, 0.60, 0.60)
        sixToEleven[15.17] = arrayOf(0.55, 0.58, 0.60, 0.65, 0.70, 0.70, 0.75, 0.75, 0.70, 0.65, 0.60, 0.42, 0.42, 0.45, 0.65, 0.65, 0.60, 0.50, 0.50, 0.68, 0.68, 0.65, 0.63, 0.63)
        sixToEleven[16.50] = arrayOf(0.60, 0.63, 0.65, 0.70, 0.70, 0.70, 0.80, 0.80, 0.80, 0.70, 0.60, 0.45, 0.45, 0.50, 0.65, 0.70, 0.60, 0.55, 0.55, 0.75, 0.75, 0.70, 0.65, 0.65)

        twelveToSeventeen[10.70] = arrayOf(0.30, 0.30, 0.30, 0.30, 0.40, 0.40, 0.60, 0.60, 0.60, 0.40, 0.35, 0.30, 0.30, 0.35, 0.45, 0.50, 0.40, 0.30, 0.30, 0.40, 0.50, 0.40, 0.40, 0.30)
        twelveToSeventeen[11.10] = arrayOf(0.30, 0.30, 0.30, 0.35, 0.40, 0.45, 0.60, 0.60, 0.60, 0.40, 0.40, 0.30, 0.35, 0.40, 0.50, 0.50, 0.30, 0.30, 0.30, 0.50, 0.50, 0.50, 0.30, 0.30)
        twelveToSeventeen[11.60] = arrayOf(0.30, 0.30, 0.35, 0.45, 0.45, 0.50, 0.65, 0.65, 0.65, 0.45, 0.40, 0.40, 0.40, 0.40, 0.50, 0.55, 0.55, 0.45, 0.45, 0.50, 0.50, 0.50, 0.40, 0.40)
        twelveToSeventeen[13.00] = arrayOf(0.40, 0.40, 0.40, 0.50, 0.55, 0.60, 0.70, 0.70, 0.70, 0.60, 0.50, 0.40, 0.40, 0.40, 0.50, 0.60, 0.60, 0.50, 0.50, 0.60, 0.60, 0.60, 0.40, 0.30)
        twelveToSeventeen[15.60] = arrayOf(0.45, 0.50, 0.50, 0.60, 0.65, 0.70, 0.80, 0.80, 0.80, 0.70, 0.60, 0.60, 0.50, 0.50, 0.60, 0.70, 0.70, 0.60, 0.60, 0.60, 0.70, 0.70, 0.50, 0.50)
        twelveToSeventeen[17.00] = arrayOf(0.50, 0.55, 0.60, 0.70, 0.75, 0.80, 1.00, 1.00, 1.00, 0.80, 0.70, 0.60, 0.60, 0.60, 0.70, 0.80, 0.80, 0.65, 0.65, 0.65, 0.70, 0.70, 0.60, 0.60)
        twelveToSeventeen[18.00] = arrayOf(0.60, 0.65, 0.70, 0.80, 0.85, 0.90, 1.10, 1.10, 1.10, 0.90, 0.80, 0.60, 0.60, 0.60, 0.70, 0.80, 0.80, 0.70, 0.65, 0.70, 0.75, 0.70, 0.60, 0.60)
        twelveToSeventeen[20.20] = arrayOf(0.70, 0.75, 0.80, 0.90, 0.95, 1.00, 1.10, 1.10, 1.10, 0.90, 0.80, 0.70, 0.70, 0.70, 0.80, 0.90, 0.90, 0.75, 0.75, 0.75, 0.80, 0.80, 0.70, 0.70)
        twelveToSeventeen[21.60] = arrayOf(0.75, 0.80, 0.90, 0.90, 1.00, 1.00, 1.20, 1.20, 1.20, 0.90, 0.80, 0.70, 0.70, 0.70, 0.90, 1.00, 1.00, 0.80, 0.80, 0.80, 0.80, 0.80, 0.70, 0.70)
        twelveToSeventeen[23.80] = arrayOf(0.75, 0.80, 0.90, 1.00, 1.10, 1.10, 1.20, 1.20, 1.20, 1.00, 0.90, 0.80, 0.80, 0.80, 0.90, 1.10, 1.10, 0.90, 0.90, 0.90, 1.00, 1.00, 0.80, 0.80)
        twelveToSeventeen[26.10] = arrayOf(0.80, 0.80, 0.90, 1.00, 1.20, 1.20, 1.30, 1.30, 1.30, 1.10, 1.00, 0.90, 0.90, 0.90, 1.00, 1.20, 1.10, 0.90, 0.90, 1.00, 1.00, 1.00, 0.90, 0.90)
        twelveToSeventeen[28.00] = arrayOf(0.90, 0.90, 1.00, 1.10, 1.10, 1.20, 1.30, 1.30, 1.30, 1.20, 1.00, 1.00, 1.00, 1.00, 1.20, 1.20, 1.20, 1.00, 1.00, 1.10, 1.10, 1.10, 0.90, 0.90)
        twelveToSeventeen[30.10] = arrayOf(1.00, 1.00, 1.10, 1.20, 1.30, 1.40, 1.50, 1.50, 1.50, 1.30, 1.20, 1.00, 1.00, 1.00, 1.30, 1.40, 1.40, 1.00, 1.00, 1.15, 1.15, 1.10, 1.00, 1.00)
        twelveToSeventeen[32.60] = arrayOf(1.10, 1.10, 1.20, 1.20, 1.40, 1.50, 1.50, 1.50, 1.50, 1.30, 1.20, 1.10, 1.10, 1.10, 1.40, 1.50, 1.40, 1.10, 1.10, 1.20, 1.20, 1.20, 1.10, 1.10)
        twelveToSeventeen[35.20] = arrayOf(1.20, 1.20, 1.30, 1.40, 1.50, 1.60, 1.70, 1.70, 1.50, 1.40, 1.20, 1.10, 1.10, 1.10, 1.40, 1.50, 1.60, 1.40, 1.20, 1.20, 1.30, 1.30, 1.20, 1.20)
        twelveToSeventeen[39.00] = arrayOf(1.30, 1.30, 1.40, 1.60, 1.60, 1.60, 1.90, 1.90, 1.90, 1.50, 1.30, 1.20, 1.20, 1.30, 1.50, 1.60, 1.70, 1.80, 1.50, 1.50, 1.60, 1.60, 1.30, 1.30)
        twelveToSeventeen[42.80] = arrayOf(1.40, 1.40, 1.50, 1.70, 1.80, 1.80, 2.00, 2.00, 2.00, 1.80, 1.80, 1.50, 1.50, 1.50, 1.60, 1.70, 1.80, 1.90, 1.60, 1.60, 1.70, 1.70, 1.50, 1.50)
        twelveToSeventeen[47.30] = arrayOf(1.50, 1.50, 1.70, 1.70, 2.00, 2.00, 2.20, 2.30, 2.20, 2.00, 1.80, 1.60, 1.60, 1.60, 1.80, 2.00, 2.10, 1.90, 1.80, 1.80, 2.00, 2.00, 1.60, 1.60)
    }

    private fun closest(map: TreeMap<Double, Array<Double>>, key: Double): Array<Double>? {
        val low = map.floorEntry(key)
        val high = map.ceilingEntry(key)
        var res: Array<Double>? = null
        if (low != null && high != null) {
            res = if (abs(key - low.key) < abs(key - high.key))
                low.value
            else
                high.value
        } else if (low != null || high != null) {
            res = if (low != null) low.value else high!!.value
        }
        return res
    }

    private fun arrayToJson(b: Array<Double>): JSONArray {
        val basals = JSONArray()
        for (i in 0..23) {
            val time = String.format(Locale.ENGLISH, "%02d:00", i)
            basals.put(JSONObject().put("time", time).put("value", b[i].toString()).put("timeAsSeconds", i * 3600))
        }
        return basals
    }

    private fun singleValueArray(value: Double, sample: Array<Double>): JSONArray {
        val array = JSONArray()
        array.put(JSONObject().put("time", "00:00").put("value", value + sample[0]).put("timeAsSeconds", 0 * 3600))
        array.put(JSONObject().put("time", "06:00").put("value", value + sample[1]).put("timeAsSeconds", 6 * 3600))
        array.put(JSONObject().put("time", "09:00").put("value", value + sample[2]).put("timeAsSeconds", 9 * 3600))
        array.put(JSONObject().put("time", "11:00").put("value", value + sample[3]).put("timeAsSeconds", 11 * 3600))
        array.put(JSONObject().put("time", "14:00").put("value", value + sample[4]).put("timeAsSeconds", 14 * 3600))
        array.put(JSONObject().put("time", "16:00").put("value", value + sample[5]).put("timeAsSeconds", 16 * 3600))
        array.put(JSONObject().put("time", "19:00").put("value", value + sample[6]).put("timeAsSeconds", 19 * 3600))
        return array
    }

    private fun singleValueArrayFromMmolToUnits(value: Double, sample: Array<Double>, units: GlucoseUnit): JSONArray {
        val array = JSONArray()
        array.put(JSONObject().put("time", "00:00").put("value", profileUtil.fromMmolToUnits(value + sample[0], units)).put("timeAsSeconds", 0 * 3600))
        array.put(JSONObject().put("time", "06:00").put("value", profileUtil.fromMmolToUnits(value + sample[1], units)).put("timeAsSeconds", 6 * 3600))
        array.put(JSONObject().put("time", "09:00").put("value", profileUtil.fromMmolToUnits(value + sample[2], units)).put("timeAsSeconds", 9 * 3600))
        array.put(JSONObject().put("time", "11:00").put("value", profileUtil.fromMmolToUnits(value + sample[3], units)).put("timeAsSeconds", 11 * 3600))
        array.put(JSONObject().put("time", "14:00").put("value", profileUtil.fromMmolToUnits(value + sample[4], units)).put("timeAsSeconds", 14 * 3600))
        array.put(JSONObject().put("time", "16:00").put("value", profileUtil.fromMmolToUnits(value + sample[5], units)).put("timeAsSeconds", 16 * 3600))
        array.put(JSONObject().put("time", "19:00").put("value", profileUtil.fromMmolToUnits(value + sample[6], units)).put("timeAsSeconds", 19 * 3600))
        return array
    }
}