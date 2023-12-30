package app.aaps.plugins.aps.openAPSSMBAutoISF

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.plugins.aps.openAPS.AutosensData
import app.aaps.plugins.aps.openAPS.CurrentTemp
import app.aaps.plugins.aps.openAPS.GlucoseStatus
import app.aaps.plugins.aps.openAPS.IobData
import app.aaps.plugins.aps.openAPS.MealData
import app.aaps.plugins.aps.openAPS.Profile
import app.aaps.plugins.aps.openAPS.RT
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Singleton
class DetermineBasalAutoISF @Inject constructor(
    private val profileUtil: ProfileUtil,
    private val aapsLogger: AAPSLogger
) {

    private val consoleError = mutableListOf<String>()
    private val consoleLog = mutableListOf<String>()

    private fun Double.toFixed2(): String = DecimalFormat("0.00#").format(round(this, 2))

    fun round_basal(value: Double): Double = value

    // Rounds value to 'digits' decimal places
    // different for negative numbers fun round(value: Double, digits: Int): Double = BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toDouble()
    fun round(value: Double, digits: Int): Double {
        val scale = 10.0.pow(digits.toDouble())
        return round(value * scale) / scale
    }
    fun Double.withoutZeros(): String = DecimalFormat("0.##").format(this)
    fun round(value: Double): Int = value.roundToInt()

    // we expect BG to rise or fall at the rate of BGI,
    // adjusted by the rate at which BG would need to rise /
    // fall to get eventualBG to target over 2 hours
    fun calculate_expected_delta(targetBg: Double, eventualBg: Int, bgi: Double): Double {
        // (hours * mins_per_hour) / 5 = how many 5 minute periods in 2h = 24
        val fiveMinBlocks = (2 * 60) / 5
        val targetDelta = targetBg - eventualBg
        return /* expectedDelta */ round(bgi + (targetDelta / fiveMinBlocks), 1)
    }

    fun convert_bg(value: Int): String =
        profileUtil.fromMgdlToStringInUnits(value.toDouble()).replace("-0.0", "0.0")

    fun convert_bg(value: Double): String =
        profileUtil.fromMgdlToStringInUnits(value).replace("-0.0", "0.0")
    //DecimalFormat("0.#").format(profileUtil.fromMgdlToUnits(value))
    //if (profile.out_units === "mmol/L") round(value / 18, 1).toFixed(1);
    //else Math.round(value);

    fun enable_smb(profile: Profile, microBolusAllowed: Boolean, meal_data: MealData, target_bg: Double): Boolean {
        // disable SMB when a high temptarget is set
        if (!microBolusAllowed) {
            consoleError.add("SMB disabled (!microBolusAllowed)")
            return false
        } else if (!profile.allowSMB_with_high_temptarget && profile.temptargetSet && target_bg > 100) {
            consoleError.add("SMB disabled due to high temptarget of $target_bg")
            return false
        }

        // enable SMB/UAM if always-on (unless previously disabled for high temptarget)
        if (profile.enableSMB_always) {
            consoleError.add("SMB enabled due to enableSMB_always")
            return true
        }

        // enable SMB/UAM (if enabled in preferences) while we have COB
        if (profile.enableSMB_with_COB && meal_data.mealCOB > 0) {
            consoleError.add("SMB enabled for COB of ${meal_data.mealCOB}")
            return true
        }

        // enable SMB/UAM (if enabled in preferences) for a full 6 hours after any carb entry
        // (6 hours is defined in carbWindow in lib/meal/total.js)
        if (profile.enableSMB_after_carbs && meal_data.carbs > 0) {
            consoleError.add("SMB enabled for 6h after carb entry")
            return true
        }

        // enable SMB/UAM (if enabled in preferences) if a low temptarget is set
        if (profile.enableSMB_with_temptarget && (profile.temptargetSet && target_bg < 100)) {
            consoleError.add("SMB enabled for temptarget of ${convert_bg(target_bg)}")
            return true
        }

        consoleError.add("SMB disabled (no enableSMB preferences active or no condition satisfied)")
        return false
    }

    fun reason(rT: RT, msg: String) {
        if (rT.reason.toString().isNotEmpty()) rT.reason.append(". ")
        rT.reason.append(msg)
        consoleError.add(msg)
    }

    private fun getMaxSafeBasal(profile: Profile): Double =
        min(profile.max_basal, min(profile.max_daily_safety_multiplier * profile.max_daily_basal, profile.current_basal_safety_multiplier * profile.current_basal))

    fun setTempBasal(_rate: Double, duration: Int, profile: Profile, rT: RT, currenttemp: CurrentTemp): RT {
        //var maxSafeBasal = Math.min(profile.max_basal, 3 * profile.max_daily_basal, 4 * profile.current_basal);

        val maxSafeBasal = getMaxSafeBasal(profile)
        var rate = _rate
        if (rate < 0) rate = 0.0
        else if (rate > maxSafeBasal) rate = maxSafeBasal

        val suggestedRate = round_basal(rate)
        if (currenttemp.duration > (duration - 10) && currenttemp.duration <= 120 && suggestedRate <= currenttemp.rate * 1.2 && suggestedRate >= currenttemp.rate * 0.8 && duration > 0) {
            rT.reason.append(" ${currenttemp.duration}m left and ${currenttemp.rate.withoutZeros()} ~ req ${suggestedRate.withoutZeros()}U/hr: no temp required")
            return rT
        }

        if (suggestedRate == profile.current_basal) {
            if (profile.skip_neutral_temps) {
                if (currenttemp.duration > 0) {
                    reason(rT, "Suggested rate is same as profile rate, a temp basal is active, canceling current temp")
                    rT.duration = 0
                    rT.rate = 0.0
                    return rT
                } else {
                    reason(rT, "Suggested rate is same as profile rate, no temp basal is active, doing nothing")
                    return rT
                }
            } else {
                reason(rT, "Setting neutral temp basal of ${profile.current_basal}U/hr")
                rT.duration = duration
                rT.rate = suggestedRate
                return rT
            }
        } else {
            rT.duration = duration
            rT.rate = suggestedRate
            return rT
        }
    }

    fun loop_smb(microBolusAllowed: Boolean, profile: Profile, iob_data_iob: Double, iobTH_reduction_ratio: Double) : String
    {
        if ( !microBolusAllowed ) {
            return "AAPS"                                                 // see message in enable_smb
        }
        if (profile.temptargetSet && profile.enableSMB_EvenOn_OddOff!! || profile.min_bg==profile.max_bg && profile.enableSMB_EvenOn_OddOff_always!! && !profile.temptargetSet)  {
            var target = convert_bg(profile.target_bg)
            var msgType: String
            var evenTarget: Boolean
            var msgUnits: String
            var msgTail: String
            var msgEven: String
            var msg: String
            if (profile.temptargetSet) {
                msgType= "TempTarget"
            } else {
                msgType = "profile target"
            }
            if (profile.out_units == "mmol/L") {
                evenTarget = (target.toDouble()*10.0).toInt() % 2 == 0
                msgUnits   = "has "
                msgTail    = "decimal"
            } else {
                evenTarget = target.toInt() % 2 == 0
                msgUnits   = "is "
                msgTail    = "number"
            }
            if ( evenTarget ) {
                msgEven    = "even "
            } else {
                msgEven    = "odd "
            }
            var iobTHeffective = profile.iob_threshold_percent!!
            if ( !evenTarget ) {
                consoleLog.add("SMB disabled; $msgType $target $msgUnits $msgEven $msgTail")
                consoleLog.add("Loop at minimum power")
                return "blocked"
            } else if ( profile.max_iob==0.0 ) {
                consoleLog.add("SMB disabled because of max_iob=0")
                return "blocked"
            } else if (iobTHeffective/100 < iob_data_iob/(profile.max_iob*iobTH_reduction_ratio)) {
                if (iobTH_reduction_ratio != 1.0) {
                    consoleLog.add("Full Loop modified max_iob ${profile.max_iob} to effectively ${round(profile.max_iob*iobTH_reduction_ratio,2)} due to profile % and/or exercise mode")
                    msg = "effective maxIOB ${round(profile.max_iob*iobTH_reduction_ratio,2)}"
                } else {
                    msg = "maxIOB ${profile.max_iob}"
                }
                consoleLog.add("SMB disabled by Full Loop logic: iob ${iob_data_iob} is more than $iobTHeffective% of $msg")
                consoleLog.add("Full Loop capped");
                return "iobTH";
            } else {
                consoleLog.add("SMB enabled; $msgType $target $msgUnits $msgEven $msgTail")
                if (profile.target_bg<100) {     // indirect assessment; later set it in GUI
                    consoleLog.add("Loop at full power")
                    return "fullLoop"                                      // even number
                } else {
                    consoleLog.add("Loop at medium power")
                    return "enforced"                                      // even number
                }
            }
        }
        consoleLog.add("Full Loop disabled")
        return "AAPS"                                                      // leave it to standard AAPS
    }

    fun interpolate(xdata: Double, profile: Profile, type: String): Double
    {   // interpolate ISF behaviour based on polygons defining nonlinear functions defined by value pairs for ...
        val polyX: Array<Double>
        val polyY: Array<Double>
        if (type == "bg") {
            //  ...             <----------------------  glucose  ---------------------->
            polyX = arrayOf(50.0, 60.0, 80.0, 90.0, 100.0, 110.0, 150.0, 180.0, 200.0)
            polyY = arrayOf(-0.5, -0.5, -0.3, -0.2,   0.0,   0.0,   0.5,   0.7,   0.7)
        } else  {
            //  ...             <-------  delta  -------->
            polyX = arrayOf(2.0, 7.0, 12.0, 16.0, 20.0)
            polyY = arrayOf(0.0, 0.0,  0.4,  0.7,  0.7)
        }
        val polymax: Int = polyX.size-1
        var step = polyX[0]
        var sVal = polyY[0]
        var stepT= polyX[polymax]
        var sValold = polyY[polymax]

        var newVal = 1.0
        var lowVal = 1.0
        var topVal = 1.0
        var lowX = 1.0
        var topX = 1.0
        var myX = 1.0
        var lowLabl = step

        if (step > xdata) {
            // extrapolate backwards
            stepT = polyX[1]
            sValold = polyY[1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal-lowVal)/(topX-lowX)*(myX-lowX)
        } else if (stepT < xdata) {
            // extrapolate forwards
            step   = polyX[polymax-1]
            sVal   = polyY[polymax-1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal-lowVal)/(topX-lowX)*(myX-lowX)
        } else {
            // interpolate
            for ( i: Int in 0..polymax) {
                step = polyX[i]
                sVal = polyY[i]
                if (step == xdata) {
                    newVal = sVal
                    break
                } else if (step > xdata) {
                    topVal = sVal
                    lowX= lowLabl
                    myX = xdata
                    topX= step
                    newVal = lowVal + (topVal-lowVal)/(topX-lowX)*(myX-lowX)
                    break
                }
                lowVal = sVal
                lowLabl= step
            }
        }
        if (type == "delta") {newVal = newVal * profile.delta_ISFrange_weight!!}      // delta range
        else if ( xdata>100) {newVal = newVal * profile.higher_ISFrange_weight!!}     // higher BG range
        else                 {newVal = newVal * profile.lower_ISFrange_weight!!}      // lower BG range
        return newVal
    }

    fun withinISFlimits(liftISF: Double, minISFReduction: Double, maxISFReduction: Double, sensitivityRatio: Double, origin_sens: String, profile: Profile,
        high_temptarget_raises_sensitivity: Boolean, target_bg: Double, normalTarget: Int): Double {
        var liftISFlimited: Double = liftISF
        if ( liftISF < minISFReduction ) {
            consoleLog.add("weakest autoISF factor ${round(liftISF,2)} limited by autoISF_min $minISFReduction")
            liftISFlimited = minISFReduction
        } else if ( liftISF > maxISFReduction ) {
            consoleLog.add("strongest autoISF factor ${round(liftISF,2)} limited by autoISF_max $maxISFReduction")
            liftISFlimited = maxISFReduction
        }
        var finalISF = 1.0
        var origin_sens_final = origin_sens
        if ( high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget ) {
            finalISF = liftISFlimited * sensitivityRatio
            origin_sens_final = " including exercise mode impact"
        } else if ( liftISFlimited >= 1 ) {
            finalISF = max(liftISFlimited, sensitivityRatio)
        } else {
            finalISF = min(liftISFlimited, sensitivityRatio)
        }
        consoleLog.add("final ISF factor is ${round(finalISF,2)}" + origin_sens_final)
        consoleLog.add("----------------------------------")
        consoleLog.add("end autoISF")
        consoleLog.add("----------------------------------")
        return finalISF
    }

    fun determine_varSMBratio(profile: Profile, bg: Int, target_bg: Double, loop_wanted_smb: String): Double
    {   // let SMB delivery ratio increase from min to max depending on how much bg exceeds target
        val profTarget = profile.sensitivity_raises_target
        var smb_delivery_ratio_bg_range = profile.smb_delivery_ratio_bg_range!!
        if ( smb_delivery_ratio_bg_range<10 )   { smb_delivery_ratio_bg_range = smb_delivery_ratio_bg_range * 18 }  // was in mmol/l
        var fix_SMB: Double = profile.smb_delivery_ratio!!
        var lower_SMB = min(profile.smb_delivery_ratio_min!!, profile.smb_delivery_ratio_max!!)
        var higher_SMB = max(profile.smb_delivery_ratio_min!!, profile.smb_delivery_ratio_max!!)
        var higher_bg = target_bg + smb_delivery_ratio_bg_range!!
        var new_SMB: Double = fix_SMB
        if ( smb_delivery_ratio_bg_range > 0 ) {
            new_SMB = lower_SMB + (higher_SMB-lower_SMB)*(bg-target_bg) / smb_delivery_ratio_bg_range
            new_SMB = max(lower_SMB, min(higher_SMB, new_SMB))   // cap if outside target_bg--higher_bg
        }
        if ( loop_wanted_smb=="fullLoop") {                                // go for max impact
            consoleLog.add("SMB delivery ratio set to ${max(fix_SMB, new_SMB)} as max of fixed and interpolated values")
            return max(fix_SMB, new_SMB)
        }

        if ( profile.smb_delivery_ratio_bg_range==0.0) {                     // deactivated in SMB extended menu
            consoleLog.add("SMB delivery ratio set to fixed value $fix_SMB")
            return fix_SMB
        }
        if (bg <= target_bg) {
            consoleLog.add("SMB delivery ratio limited by minimum value $lower_SMB")
            return lower_SMB
        }
        if (bg >= higher_bg) {
            consoleLog.add("SMB delivery ratio limited by maximum value $higher_SMB")
            return higher_SMB
        }
        consoleLog.add("SMB delivery ratio set to interpolated value $new_SMB")
        return new_SMB
    }

    fun autoISF(sens: Double, origin_sens: String, target_bg: Double, profile: Profile, glucose_status: GlucoseStatus, meal_data: MealData, currentTime: Long,
    autosens_data: AutosensData, sensitivityRatio: Double, loop_wanted_smb: String, high_temptarget_raises_sensitivity: Boolean, normalTarget: Int): Double
    {   if ( !profile.enable_autoISF!!) {
        consoleLog.add("autoISF disabled in Preferences")
        consoleLog.add("----------------------------------")
        consoleLog.add("end autoISF")
        consoleLog.add("----------------------------------")

        return sens
    }
        var dura05: Double? = glucose_status.duraISFminutes
        var avg05: Double?  = glucose_status.duraISFaverage
        var maxISFReduction: Double = profile.autoISF_max!!
        var sens_modified = false
        var pp_ISF = 1.0
        var delta_ISF = 1.0
        var acce_ISF = 1.0
        var acce_weight: Double = 1.0
        var bg_off = target_bg+10.0 - glucose_status.glucose;                      // move from central BG=100 to target+10 as virtual BG'=100

        // calculate acce_ISF from bg acceleration and adapt ISF accordingly
        var fit_corr: Double = glucose_status.corrSqu!!
        var bg_acce: Double = glucose_status.bgAcceleration!!
        if (glucose_status.a2 !=0.0 && fit_corr>=0.9) {
            var minmax_delta: Double = - glucose_status.a1!!/2/glucose_status.a2!! * 5      // back from 5min block to 1 min
            var minmax_value: Double = round(glucose_status.a0!! - minmax_delta*minmax_delta/25*glucose_status.a2!!, 1)
            minmax_delta = round(minmax_delta, 1)
            if (minmax_delta>0 && bg_acce<0) {
                consoleLog.add("Parabolic fit extrapolates a maximum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
            } else if (minmax_delta>0 && bg_acce>0.0) {
                consoleLog.add("Parabolic fit extrapolates a minimum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
                if (minmax_delta<=30 && minmax_value<target_bg) {   // start braking
                    acce_weight = -profile.bgBrake_ISF_weight!!
                    consoleLog.add("extrapolation below target soon: use bgBrake_ISF_weight of ${-acce_weight}")
                }
            }
        }
        if ( fit_corr<0.9 ) {
            consoleLog.add("acce_ISF adaptation by-passed as correlation ${round(fit_corr,3)} is too low")
        } else {
            var fit_share = 10*(fit_corr-0.9)                                // 0 at correlation 0.9, 1 at 1.00
            var cap_weight = 1.0                                             // full contribution above target
            if ( acce_weight==1.0 && glucose_status.glucose<profile.target_bg ) { // below target acce goes towards target
                if ( bg_acce > 0 ) {
                    if ( bg_acce>1)            { cap_weight = 0.5 }            // halve the effect below target
                    acce_weight = profile.bgBrake_ISF_weight!!
                } else if ( bg_acce < 0 ) {
                    acce_weight = profile.bgAccel_ISF_weight!!
                }
            } else if ( acce_weight==1.0) {                                       // above target acce goes away from target
                if ( bg_acce < 0.0 ) {
                    acce_weight = profile.bgBrake_ISF_weight!!
                } else if ( bg_acce > 0.0 ) {
                    acce_weight = profile.bgAccel_ISF_weight!!
                }
            }
            acce_ISF = 1.0 + bg_acce * cap_weight * acce_weight * fit_share
            consoleLog.add("acce_ISF adaptation is ${round(acce_ISF,2)}")
            if ( acce_ISF != 1.0 ) {
                sens_modified = true
            }
        }

        var bg_ISF = 1 + interpolate(100-bg_off, profile, "bg")
        consoleLog.add("bg_ISF adaptation is ${round(bg_ISF,2)}")
        var liftISF = 1.0
        var final_ISF = 1.0
        if (bg_ISF<1.0) {
            liftISF = min(bg_ISF, acce_ISF)
            if ( acce_ISF>1.0 ) {
                liftISF = bg_ISF * acce_ISF                                 // bg_ISF could become > 1 now
                consoleLog.add("bg_ISF adaptation lifted to ${round(liftISF,2)} as bg accelerates already")
            }
            final_ISF = withinISFlimits(liftISF, profile.autoISF_min!!, maxISFReduction, sensitivityRatio, origin_sens, profile, high_temptarget_raises_sensitivity, target_bg, normalTarget)
            return min(720.0, round(profile.sens / final_ISF, 1))         // observe ISF maximum of 720(?)
        } else if ( bg_ISF > 1.0 ) {
            sens_modified = true
        }

        var bg_delta = glucose_status.delta
        var deltaType: String
        if (profile.enable_pp_ISF_always!! || profile.pp_ISF_hours!! >= (currentTime - meal_data.lastCarbTime) / 1000/3600) {
            deltaType = "pp"
        } else {
            deltaType = "delta"
        }
        if (bg_off > 0.0) {
            consoleLog.add(deltaType+"_ISF adaptation by-passed as average glucose < $target_bg+10")
        } else if (glucose_status.short_avgdelta<0) {
            consoleLog.add(deltaType+"_ISF adaptation by-passed as no rise or too short lived")
        } else if (deltaType == "pp") {
            pp_ISF = 1.0 + max(0.0, bg_delta * profile.pp_ISF_weight!!)
            consoleLog.add("pp_ISF adaptation is ${round(pp_ISF,2)}")
            if (pp_ISF != 1.0) {
                sens_modified = true
            }

        } else {
            delta_ISF = interpolate(bg_delta, profile, "delta");
            //  mod V14d: halve the effect below target_bg+30
            if ( bg_off > -20.0 ) {
                delta_ISF = 0.5 * delta_ISF
            }
            delta_ISF = 1.0 + delta_ISF
            consoleLog.add("delta_ISF adaptation is ${round(delta_ISF,2)}")

            if (delta_ISF != 1.0) {
                sens_modified = true
            }
        }

        var dura_ISF: Double = 1.0
        var weightISF: Double? = profile.dura_ISF_weight
        if (meal_data.mealCOB>0 && !profile.enable_dura_ISF_with_COB!!) {
            consoleLog.add("dura_ISF by-passed; preferences disabled mealCOB of ${round(meal_data.mealCOB,1)}")
        } else if (dura05!!<10.0) {
            consoleLog.add("dura_ISF by-passed; bg is only $dura05 m at level $avg05");
        } else if (avg05!! <= target_bg) {
            consoleLog.add("dura_ISF by-passed; avg. glucose $avg05 below target $target_bg")
        } else {
            // fight the resistance at high levels
            var dura05Weight = dura05!! / 60
            var avg05Weight = weightISF!! / target_bg
            dura_ISF += dura05Weight*avg05Weight*(avg05!!-target_bg)
            sens_modified = true
            consoleLog.add("dura_ISF adaptation is ${round(dura_ISF,2)} because ISF ${round(sens,1)} did not do it for ${round(dura05,1)}m")
        }
        if ( sens_modified ) {
            liftISF = max(dura_ISF, max(bg_ISF, max(delta_ISF, max(acce_ISF, pp_ISF))))
            if ( acce_ISF < 1.0 ) {                                                                           // 13.JAN.2022 brakes on for otherwise stronger or stable ISF
                consoleLog.add("strongest autoISF factor ${round(liftISF,2)} weakened to ${round(liftISF*acce_ISF,2)} as bg decelerates already")
                liftISF = liftISF * acce_ISF                                                               // brakes on for otherwise stronger or stable ISF
            }                                                                                               // brakes on for otherwise stronger or stable ISF
            final_ISF = withinISFlimits(liftISF, profile.autoISF_min!!, maxISFReduction!!, sensitivityRatio, origin_sens, profile, high_temptarget_raises_sensitivity, target_bg, normalTarget)
            return round(profile.sens / final_ISF, 1)
        }
        consoleLog.add("----------------------------------")
        consoleLog.add("end autoISF")
        consoleLog.add("----------------------------------")
        return sens     // nothing changed
    }

    fun determine_basal(
        glucose_status: GlucoseStatus, currenttemp: CurrentTemp, iob_data_array: IobData, profile: Profile, autosens_data: AutosensData, meal_data: MealData,
        microBolusAllowed: Boolean, currentTime: Long, flatBGsDetected: Boolean
    ): RT {
        var rT = RT(
            consoleLog = consoleLog,
            consoleError = consoleError
        )

        // TODO eliminate
        val deliverAt = currentTime

        // TODO eliminate
        val profile_current_basal = round_basal(profile.current_basal)
        var basal = profile_current_basal

        // TODO eliminate
        val systemTime = currentTime

        // TODO eliminate
        val bgTime = glucose_status.date
        val minAgo = round((systemTime - bgTime) / 60.0 / 1000.0, 1)
        // TODO eliminate
        val bg = glucose_status.glucose
        // TODO eliminate
        val noise = glucose_status.noise
        // 38 is an xDrip error state that usually indicates sensor failure
        // all other BG values between 11 and 37 mg/dL reflect non-error-code BG values, so we should zero temp for those
        if (bg <= 10 || bg == 38.0 || noise >= 3) {  //Dexcom is in ??? mode or calibrating, or xDrip reports high noise
            rT.reason.append("CGM is calibrating, in ??? state, or noise is high")
        }
        if (minAgo > 12 || minAgo < -5) { // Dexcom data is too old, or way in the future
            rT.reason.append("If current system time $systemTime is correct, then BG data is too old. The last BG data was read ${minAgo}m ago at $bgTime")
            // if BG is too old/noisy, or is changing less than 1 mg/dL/5m for 45m, cancel any high temps and shorten any long zero temps
        } else if (bg > 60 && flatBGsDetected) {
            rT.reason.append("Error: CGM data is unchanged for the past ~45m")
        }
        if (bg <= 10 || bg == 38.0 || noise >= 3 || minAgo > 12 || minAgo < -5 || (bg > 60 && flatBGsDetected)) {
            if (currenttemp.rate > basal) { // high temp is running
                rT.reason.append(". Replacing high temp basal of ${currenttemp.rate} with neutral temp of $basal")
                rT.deliverAt = deliverAt
                rT.duration = 30
                rT.rate = basal
                return rT
            } else if (currenttemp.rate == 0.0 && currenttemp.duration > 30) { //shorten long zero temps to 30m
                rT.reason.append(". Shortening " + currenttemp.duration + "m long zero temp to 30m. ")
                rT.deliverAt = deliverAt
                rT.duration = 30
                rT.rate = 0.0
                return rT
            } else { //do nothing.
                rT.reason.append(". Temp ${currenttemp.rate} <= current basal ${round(basal, 2)}U/hr; doing nothing. ")
                return rT
            }
        }

        // TODO eliminate
        val max_iob = profile.max_iob // maximum amount of non-bolus IOB OpenAPS will ever deliver

        // if min and max are set, then set target to their average
        var target_bg = (profile.min_bg + profile.max_bg) / 2
        var min_bg = profile.min_bg
        var max_bg = profile.max_bg

        var sensitivityRatio: Double = 1.0
        var origin_sens = ""
        var exercise_ratio = 1.0

        val high_temptarget_raises_sensitivity = profile.exercise_mode || profile.high_temptarget_raises_sensitivity
        val normalTarget = 100 // evaluate high/low temptarget against 100, not scheduled target (which might change)
        // when temptarget is 160 mg/dL, run 50% basal (120 = 75%; 140 = 60%),  80 mg/dL with low_temptarget_lowers_sensitivity would give 1.5x basal, but is limited to autosens_max (1.2x by default)
        val halfBasalTarget = profile.half_basal_exercise_target

        if (high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget
            || profile.low_temptarget_lowers_sensitivity && profile.temptargetSet && target_bg < normalTarget) {
            if (high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget
                || profile.low_temptarget_lowers_sensitivity && profile.temptargetSet && target_bg < normalTarget ) {
                // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
                // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
                //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
                val c = (halfBasalTarget - normalTarget).toDouble()
                if (c * (c + target_bg-normalTarget) <= 0.0) {
                    sensitivityRatio = profile.autosens_max
                } else {
                    sensitivityRatio = c / (c + target_bg - normalTarget)
                    // limit sensitivityRatio to profile.autosens_max (1.2x by default)
                    sensitivityRatio = min(sensitivityRatio, profile.autosens_max)
                    sensitivityRatio = round(sensitivityRatio, 2)
                    exercise_ratio = sensitivityRatio
                    origin_sens = "from TT modifier"
                    consoleLog.add("Sensitivity ratio set to $sensitivityRatio based on temp target of $target_bg; ")
                }
            } else {
                sensitivityRatio = autosens_data.ratio
                consoleLog.add("Autosens ratio: $sensitivityRatio; ")
            }
        }
        val iobTH_reduction_ratio = profile.profile_percentage!! / 100 * exercise_ratio ;     // later: * activityRatio;
        basal = profile.current_basal * sensitivityRatio
        basal = round_basal(basal)
        if (basal != profile_current_basal)
            consoleLog.add("Adjusting basal from $profile_current_basal to $basal; ")
        else
            consoleLog.add("Basal unchanged: $basal; ")

        // adjust min, max, and target BG for sensitivity, such that 50% increase in ISF raises target from 100 to 120
        if (profile.temptargetSet) {
            //console.log("Temp Target set, not adjusting with autosens; ");
        } else {
            if (profile.sensitivity_raises_target && autosens_data.ratio < 1 || profile.resistance_lowers_target && autosens_data.ratio > 1) {
                // with a target of 100, default 0.7-1.2 autosens min/max range would allow a 93-117 target range
                min_bg = round((min_bg - 60) / autosens_data.ratio, 0) + 60
                max_bg = round((max_bg - 60) / autosens_data.ratio, 0) + 60
                var new_target_bg = round((target_bg - 60) / autosens_data.ratio, 0) + 60
                // don't allow target_bg below 80
                new_target_bg = max(80.0, new_target_bg)
                if (target_bg == new_target_bg)
                    consoleLog.add("target_bg unchanged: $new_target_bg; ")
                else
                    consoleLog.add("target_bg from $target_bg to $new_target_bg; ")

                target_bg = new_target_bg
            }
        }

        val iobArray = iob_data_array
        val iob_data = iobArray[0]

        val tick: String

        tick = if (glucose_status.delta > -0.5) {
            "+" + round(glucose_status.delta)
        } else {
            round(glucose_status.delta).toString()
        }
        val minDelta = min(glucose_status.delta, glucose_status.short_avgdelta)
        val minAvgDelta = min(glucose_status.short_avgdelta, glucose_status.long_avgdelta)
        val maxDelta = max(glucose_status.delta, max(glucose_status.short_avgdelta, glucose_status.long_avgdelta))

        val profile_sens = round(profile.sens, 1)
        var sens = round(profile.sens / sensitivityRatio, 1)
        if (sens != profile_sens) {
            consoleLog.add("ISF from $profile_sens to $sens")
        } else {
            consoleLog.add("ISF unchanged: $sens")
        }
        //console.log(" (autosens ratio "+sensitivityRatio+")");
        consoleError.add("CR:${profile.carb_ratio}")

        consoleLog.add("----------------------------------")
        consoleLog.add("start autoISF ${profile.autoISF_version!!}")  // fit onto narrow screens
        consoleLog.add("----------------------------------")
        var loop_wanted_smb = loop_smb(microBolusAllowed, profile, iob_data.iob, iobTH_reduction_ratio)
        var enableSMB = false
        if (microBolusAllowed && loop_wanted_smb != "AAPS") {
            if ( loop_wanted_smb=="enforced" || loop_wanted_smb=="fullLoop" ) {              // otherwise FL switched SMB off
                enableSMB = true
            }
        } else { enableSMB = enable_smb(
            profile,
            microBolusAllowed,
            meal_data,
            target_bg
            )
        }

        sens = autoISF(sens, origin_sens, target_bg, profile, glucose_status, meal_data, currentTime, autosens_data, sensitivityRatio, loop_wanted_smb, high_temptarget_raises_sensitivity, normalTarget);

        // whole code block missing about lastTempAge, tempModulus, etc.

        //calculate BG impact: the amount BG "should" be rising or falling based on insulin activity alone
        val bgi = round((-iob_data.activity * sens * 5), 2)
        // project deviations for 30 minutes
        var deviation = round(30 / 5 * (minDelta - bgi))
        // don't overreact to a big negative delta: use minAvgDelta if deviation is negative
        if (deviation < 0) {
            deviation = round((30 / 5) * (minAvgDelta - bgi))
            // and if deviation is still negative, use long_avgdelta
            if (deviation < 0) {
                deviation = round((30 / 5) * (glucose_status.long_avgdelta - bgi))
            }
        }

        // calculate the naive (bolus calculator math) eventual BG based on net IOB and sensitivity
        val naive_eventualBG: Int =
            if (iob_data.iob > 0) round(bg - (iob_data.iob * sens))
            else  // if IOB is negative, be more conservative and use the lower of sens, profile.sens
                round(bg - (iob_data.iob * min(sens, profile.sens)))

        // and adjust it for the deviation above
        var eventualBG: Int = naive_eventualBG + deviation

        // raise target for noisy / raw CGM data
        if (bg > max_bg && profile.adv_target_adjustments && !profile.temptargetSet) {
            // with target=100, as BG rises from 100 to 160, adjustedTarget drops from 100 to 80
            val adjustedMinBG = round(max(80.0, min_bg - (bg - min_bg) / 3.0), 0)
            val adjustedTargetBG = round(max(80.0, target_bg - (bg - target_bg) / 3.0), 0)
            val adjustedMaxBG = round(max(80.0, max_bg - (bg - max_bg) / 3.0), 0)
            // if eventualBG, naive_eventualBG, and target_bg aren't all above adjustedMinBG, don’t use it
            //console.error("naive_eventualBG:",naive_eventualBG+", eventualBG:",eventualBG);
            if (eventualBG > adjustedMinBG && naive_eventualBG > adjustedMinBG && min_bg > adjustedMinBG) {
                consoleLog.add("Adjusting targets for high BG: min_bg from $min_bg to $adjustedMinBG; ")
                min_bg = adjustedMinBG
            } else {
                consoleLog.add("min_bg unchanged: $min_bg; ")
            }
            // if eventualBG, naive_eventualBG, and target_bg aren't all above adjustedTargetBG, don’t use it
            if (eventualBG > adjustedTargetBG && naive_eventualBG > adjustedTargetBG && target_bg > adjustedTargetBG) {
                consoleLog.add("target_bg from $target_bg to $adjustedTargetBG; ")
                target_bg = adjustedTargetBG
            } else {
                consoleLog.add("target_bg unchanged: $target_bg; ")
            }
            // if eventualBG, naive_eventualBG, and max_bg aren't all above adjustedMaxBG, don’t use it
            if (eventualBG > adjustedMaxBG && naive_eventualBG > adjustedMaxBG && max_bg > adjustedMaxBG) {
                consoleError.add("max_bg from $max_bg to $adjustedMaxBG")
                max_bg = adjustedMaxBG
            } else {
                consoleError.add("max_bg unchanged: $max_bg")
            }
        }

        val expectedDelta = calculate_expected_delta(target_bg, eventualBG, bgi)

        // min_bg of 90 -> threshold of 65, 100 -> 70 110 -> 75, and 130 -> 85
        val threshold = min_bg - 0.5 * (min_bg - 40)

        //console.error(reservoir_data);

        rT = RT(
            bg = bg,
            tick = tick,
            eventualBG = eventualBG,
            targetBG = target_bg,
            insulinReq = 0.0,
            deliverAt = deliverAt, // The time at which the microbolus should be delivered
            sensitivityRatio = sensitivityRatio, // autosens ratio (fraction of normal basal)
            consoleLog = consoleLog,
            consoleError = consoleError
        )

        // generate predicted future BGs based on IOB, COB, and current absorption rate

        var COBpredBGs = mutableListOf<Double>()
        var aCOBpredBGs = mutableListOf<Double>()
        var IOBpredBGs = mutableListOf<Double>()
        var UAMpredBGs = mutableListOf<Double>()
        var ZTpredBGs = mutableListOf<Double>()
        COBpredBGs.add(bg)
        aCOBpredBGs.add(bg)
        IOBpredBGs.add(bg)
        ZTpredBGs.add(bg)
        UAMpredBGs.add(bg)

        //var enableSMB = enable_smb(profile, microBolusAllowed, meal_data, target_bg)// pulled ahead for AutoISF

        // enable UAM (if enabled in preferences)
        val enableUAM = profile.enableUAM

        //console.error(meal_data);
        // carb impact and duration are 0 unless changed below
        var ci: Double
        val cid: Double
        // calculate current carb absorption rate, and how long to absorb all carbs
        // CI = current carb impact on BG in mg/dL/5m
        ci = round((minDelta - bgi), 1)
        val uci = round((minDelta - bgi), 1)
        // ISF (mg/dL/U) / CR (g/U) = CSF (mg/dL/g)

        // TODO: remove commented-out code for old behavior
        //if (profile.temptargetSet) {
        // if temptargetSet, use unadjusted profile.sens to allow activity mode sensitivityRatio to adjust CR
        //var csf = profile.sens / profile.carb_ratio;
        //} else {
        // otherwise, use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments
        // so that autotuned CR is still in effect even when basals and ISF are being adjusted by autosens
        //var csf = sens / profile.carb_ratio;
        //}
        // use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments so that
        // autotuned CR is still in effect even when basals and ISF are being adjusted by TT or autosens
        // this avoids overdosing insulin for large meals when low temp targets are active
        val csf = sens / profile.carb_ratio
        consoleError.add("profile.sens: ${profile.sens}, sens: $sens, CSF: $csf")

        val maxCarbAbsorptionRate = 30 // g/h; maximum rate to assume carbs will absorb if no CI observed
        // limit Carb Impact to maxCarbAbsorptionRate * csf in mg/dL per 5m
        val maxCI = round(maxCarbAbsorptionRate * csf * 5 / 60, 1)
        if (ci > maxCI) {
            consoleError.add("Limiting carb impact from $ci to $maxCI mg/dL/5m ( $maxCarbAbsorptionRate g/h )")
            ci = maxCI
        }
        var remainingCATimeMin = 3.0 // h; duration of expected not-yet-observed carb absorption
        // adjust remainingCATime (instead of CR) for autosens if sensitivityRatio defined
        remainingCATimeMin = remainingCATimeMin / sensitivityRatio
        // 20 g/h means that anything <= 60g will get a remainingCATimeMin, 80g will get 4h, and 120g 6h
        // when actual absorption ramps up it will take over from remainingCATime
        val assumedCarbAbsorptionRate = 20 // g/h; maximum rate to assume carbs will absorb if no CI observed
        var remainingCATime = remainingCATimeMin
        if (meal_data.carbs > 0) {
            // if carbs * assumedCarbAbsorptionRate > remainingCATimeMin, raise it
            // so <= 90g is assumed to take 3h, and 120g=4h
            remainingCATimeMin = Math.max(remainingCATimeMin, meal_data.mealCOB / assumedCarbAbsorptionRate)
            val lastCarbAge = round((systemTime - meal_data.lastCarbTime) / 60000.0)
            //console.error(meal_data.lastCarbTime, lastCarbAge);

            val fractionCOBAbsorbed = (meal_data.carbs - meal_data.mealCOB) / meal_data.carbs
            remainingCATime = remainingCATimeMin + 1.5 * lastCarbAge / 60
            remainingCATime = round(remainingCATime, 1)
            //console.error(fractionCOBAbsorbed, remainingCATimeAdjustment, remainingCATime)
            consoleError.add("Last carbs " + lastCarbAge + "minutes ago; remainingCATime:" + remainingCATime + "hours;" + round(fractionCOBAbsorbed * 100) + "% carbs absorbed")
        }

        // calculate the number of carbs absorbed over remainingCATime hours at current CI
        // CI (mg/dL/5m) * (5m)/5 (m) * 60 (min/hr) * 4 (h) / 2 (linear decay factor) = total carb impact (mg/dL)
        val totalCI = Math.max(0.0, ci / 5 * 60 * remainingCATime / 2)
        // totalCI (mg/dL) / CSF (mg/dL/g) = total carbs absorbed (g)
        val totalCA = totalCI / csf
        val remainingCarbsCap: Int // default to 90
        remainingCarbsCap = min(90, profile.remainingCarbsCap)
        var remainingCarbs = max(0.0, meal_data.mealCOB - totalCA)
        remainingCarbs = Math.min(remainingCarbsCap.toDouble(), remainingCarbs)
        // assume remainingCarbs will absorb in a /\ shaped bilinear curve
        // peaking at remainingCATime / 2 and ending at remainingCATime hours
        // area of the /\ triangle is the same as a remainingCIpeak-height rectangle out to remainingCATime/2
        // remainingCIpeak (mg/dL/5m) = remainingCarbs (g) * CSF (mg/dL/g) * 5 (m/5m) * 1h/60m / (remainingCATime/2) (h)
        val remainingCIpeak = remainingCarbs * csf * 5 / 60 / (remainingCATime / 2)
        //console.error(profile.min_5m_carbimpact,ci,totalCI,totalCA,remainingCarbs,remainingCI,remainingCATime);

        // calculate peak deviation in last hour, and slope from that to current deviation
        val slopeFromMaxDeviation = round(meal_data.slopeFromMaxDeviation, 2)
        // calculate lowest deviation in last hour, and slope from that to current deviation
        val slopeFromMinDeviation = round(meal_data.slopeFromMinDeviation, 2)
        // assume deviations will drop back down at least at 1/3 the rate they ramped up
        val slopeFromDeviations = Math.min(slopeFromMaxDeviation, -slopeFromMinDeviation / 3)
        //console.error(slopeFromMaxDeviation);

        val aci = 10
        //5m data points = g * (1U/10g) * (40mg/dL/1U) / (mg/dL/5m)
        // duration (in 5m data points) = COB (g) * CSF (mg/dL/g) / ci (mg/dL/5m)
        // limit cid to remainingCATime hours: the reset goes to remainingCI
        if (ci == 0.0) {
            // avoid divide by zero
            cid = 0.0
        } else {
            cid = Math.min(remainingCATime * 60 / 5 / 2, Math.max(0.0, meal_data.mealCOB * csf / ci))
        }
        val acid = Math.max(0.0, meal_data.mealCOB * csf / aci)
        // duration (hours) = duration (5m) * 5 / 60 * 2 (to account for linear decay)
        consoleError.add("Carb Impact: ${ci} mg/dL per 5m; CI Duration: ${round(cid * 5 / 60 * 2, 1)} hours; remaining CI (~2h peak): ${round(remainingCIpeak, 1)} mg/dL per 5m")
        //console.error("Accel. Carb Impact:",aci,"mg/dL per 5m; ACI Duration:",round(acid*5/60*2,1),"hours");
        var minIOBPredBG = 999
        var minCOBPredBG = 999
        var minUAMPredBG = 999
        val minGuardBG: Int
        var minCOBGuardBG = 999.0
        var minUAMGuardBG = 999.0
        var minIOBGuardBG = 999.0
        var minZTGuardBG = 999
        var minPredBG: Int
        var avgPredBG: Int
        var IOBpredBG: Double = eventualBG.toDouble()
        var maxIOBPredBG = bg
        var maxCOBPredBG = bg
        var maxUAMPredBG = bg
        //var maxPredBG = bg;
        var eventualPredBG = bg
        var lastIOBpredBG: Double
        var lastCOBpredBG: Double? = null
        var lastUAMpredBG: Double? = null
        var lastZTpredBG: Int
        var UAMduration = 0.0
        var remainingCItotal = 0.0
        val remainingCIs = mutableListOf<Int>()
        val predCIs = mutableListOf<Int>()
        var UAMpredBG: Double? = null
        var COBpredBG: Double? = null
        var aCOBpredBG: Double?
        iobArray.forEach { iobTick ->
            //console.error(iobTick);
            val predBGI: Double = round((-iobTick.activity * sens * 5), 2)
            val predZTBGI = round((-iobTick.iobWithZeroTemp!!.activity * sens * 5), 2)
            // for IOBpredBGs, predicted deviation impact drops linearly from current deviation down to zero
            // over 60 minutes (data points every 5m)
            val predDev: Double = ci * (1 - min(1.0, IOBpredBGs.size / (60.0 / 5.0)))
            IOBpredBG = IOBpredBGs[IOBpredBGs.size - 1] + predBGI + predDev
            // calculate predBGs with long zero temp without deviations
            val ZTpredBG = ZTpredBGs[ZTpredBGs.size - 1] + predZTBGI
            // for COBpredBGs, predicted carb impact drops linearly from current carb impact down to zero
            // eventually accounting for all carbs (if they can be absorbed over DIA)
            val predCI: Double = max(0.0, max(0.0, ci) * (1 - COBpredBGs.size / max(cid * 2, 1.0)))
            val predACI = max(0.0, max(0, aci) * (1 - COBpredBGs.size / max(acid * 2, 1.0)))
            // if any carbs aren't absorbed after remainingCATime hours, assume they'll absorb in a /\ shaped
            // bilinear curve peaking at remainingCIpeak at remainingCATime/2 hours (remainingCATime/2*12 * 5m)
            // and ending at remainingCATime h (remainingCATime*12 * 5m intervals)
            val intervals = Math.min(COBpredBGs.size.toDouble(), ((remainingCATime * 12) - COBpredBGs.size))
            val remainingCI = Math.max(0.0, intervals / (remainingCATime / 2 * 12) * remainingCIpeak)
            remainingCItotal += predCI + remainingCI
            remainingCIs.add(round(remainingCI))
            predCIs.add(round(predCI))
            //console.log(round(predCI,1)+"+"+round(remainingCI,1)+" ");
            COBpredBG = COBpredBGs[COBpredBGs.size - 1] + predBGI + min(0.0, predDev) + predCI + remainingCI
            aCOBpredBG = aCOBpredBGs[aCOBpredBGs.size - 1] + predBGI + min(0.0, predDev) + predACI
            // for UAMpredBGs, predicted carb impact drops at slopeFromDeviations
            // calculate predicted CI from UAM based on slopeFromDeviations
            val predUCIslope = max(0.0, uci + (UAMpredBGs.size * slopeFromDeviations))
            // if slopeFromDeviations is too flat, predicted deviation impact drops linearly from
            // current deviation down to zero over 3h (data points every 5m)
            val predUCImax = max(0.0, uci * (1 - UAMpredBGs.size / max(3.0 * 60 / 5, 1.0)))
            //console.error(predUCIslope, predUCImax);
            // predicted CI from UAM is the lesser of CI based on deviationSlope or DIA
            val predUCI = min(predUCIslope, predUCImax)
            if (predUCI > 0) {
                //console.error(UAMpredBGs.length,slopeFromDeviations, predUCI);
                UAMduration = round((UAMpredBGs.size + 1) * 5 / 60.0, 1)
            }
            UAMpredBG = UAMpredBGs[UAMpredBGs.size - 1] + predBGI + min(0.0, predDev) + predUCI
            //console.error(predBGI, predCI, predUCI);
            // truncate all BG predictions at 4 hours
            if (IOBpredBGs.size < 48) IOBpredBGs.add(IOBpredBG)
            if (COBpredBGs.size < 48) COBpredBGs.add(COBpredBG!!)
            if (aCOBpredBGs.size < 48) aCOBpredBGs.add(aCOBpredBG!!)
            if (UAMpredBGs.size < 48) UAMpredBGs.add(UAMpredBG!!)
            if (ZTpredBGs.size < 48) ZTpredBGs.add(ZTpredBG)
            // calculate minGuardBGs without a wait from COB, UAM, IOB predBGs
            if (COBpredBG!! < minCOBGuardBG) minCOBGuardBG = round(COBpredBG!!).toDouble()
            if (UAMpredBG!! < minUAMGuardBG) minUAMGuardBG = round(UAMpredBG!!).toDouble()
            if (IOBpredBG < minIOBGuardBG) minIOBGuardBG = IOBpredBG
            if (ZTpredBG < minZTGuardBG) minZTGuardBG = round(ZTpredBG)

            // set minPredBGs starting when currently-dosed insulin activity will peak
            // look ahead 60m (regardless of insulin type) so as to be less aggressive on slower insulins
            // add 30m to allow for insulin delivery (SMBs or temps)
            val insulinPeakTime = 90
            val insulinPeak5m = (insulinPeakTime / 60.0) * 12.0
            //console.error(insulinPeakTime, insulinPeak5m, profile.insulinPeakTime, profile.curve);

            // wait 90m before setting minIOBPredBG
            if (IOBpredBGs.size > insulinPeak5m && (IOBpredBG < minIOBPredBG)) minIOBPredBG = round(IOBpredBG)
            if (IOBpredBG > maxIOBPredBG) maxIOBPredBG = IOBpredBG

            // wait 85-105m before setting COB and 60m for UAM minPredBGs
            if ((cid != 0.0 || remainingCIpeak > 0) && COBpredBGs.size > insulinPeak5m && (COBpredBG!! < minCOBPredBG)) minCOBPredBG = round(COBpredBG!!)

            if ((cid != 0.0 || remainingCIpeak > 0) && COBpredBG!! > maxIOBPredBG) maxCOBPredBG = round(COBpredBG!!).toDouble()
            if (enableUAM && UAMpredBGs.size > 12 && (UAMpredBG!! < minUAMPredBG)) minUAMPredBG = round(UAMpredBG!!)
            if (enableUAM && UAMpredBG!! > maxIOBPredBG) maxUAMPredBG = round(UAMpredBG!!).toDouble()
        }
        // set eventualBG to include effect of carbs
        //console.error("PredBGs:",JSON.stringify(predBGs));
        if (meal_data.mealCOB > 0) {
            consoleError.add("predCIs (mg/dL/5m):" + predCIs.joinToString(separator = " "))
            consoleError.add("remainingCIs:      " + remainingCIs.joinToString(separator = " "))
        }
        rT.predBGs = RT.Predictions()
        IOBpredBGs = IOBpredBGs.map { round(min(401.0, max(39.0, it)), 0) }.toMutableList()
        for (i in IOBpredBGs.size - 1 downTo 13) {
            if (IOBpredBGs[i - 1] != IOBpredBGs[i]) break
            else IOBpredBGs.removeLast()
        }
        rT.predBGs?.IOB = IOBpredBGs.map { it.toInt() }
        lastIOBpredBG = round(IOBpredBGs[IOBpredBGs.size - 1]).toDouble()
        ZTpredBGs = ZTpredBGs.map { round(min(401.0, max(39.0, it)), 0) }.toMutableList()
        for (i in ZTpredBGs.size - 1 downTo 7) {
            // stop displaying ZTpredBGs once they're rising and above target
            if (ZTpredBGs[i - 1] >= ZTpredBGs[i] || ZTpredBGs[i] <= target_bg) break
            else ZTpredBGs.removeLast()
        }
        rT.predBGs?.ZT = ZTpredBGs.map { it.toInt() }
        if (meal_data.mealCOB > 0) {
            aCOBpredBGs = aCOBpredBGs.map { round(min(401.0, max(39.0, it)), 0) }.toMutableList()
            for (i in aCOBpredBGs.size - 1 downTo 13) {
                if (aCOBpredBGs[i - 1] != aCOBpredBGs[i]) break
                else aCOBpredBGs.removeLast()
            }
        }
        if (meal_data.mealCOB > 0 && (ci > 0 || remainingCIpeak > 0)) {
            COBpredBGs = COBpredBGs.map { round(min(401.0, max(39.0, it)), 0) }.toMutableList()
            for (i in COBpredBGs.size - 1 downTo 13) {
                if (COBpredBGs[i - 1] != COBpredBGs[i]) break
                else COBpredBGs.removeLast()
            }
            rT.predBGs?.COB = COBpredBGs.map { it.toInt() }
            lastCOBpredBG = COBpredBGs[COBpredBGs.size - 1]
            eventualBG = max(eventualBG, round(COBpredBGs[COBpredBGs.size - 1]))
        }
        if (ci > 0 || remainingCIpeak > 0) {
            if (enableUAM) {
                UAMpredBGs = UAMpredBGs.map { round(min(401.0, max(39.0, it)), 0) }.toMutableList()
                for (i in UAMpredBGs.size - 1 downTo 13) {
                    if (UAMpredBGs[i - 1] != UAMpredBGs[i]) break

                    else UAMpredBGs.removeLast()

                }
                rT.predBGs?.UAM = UAMpredBGs.map { it.toInt() }
                lastUAMpredBG = UAMpredBGs[UAMpredBGs.size - 1]
                eventualBG = max(eventualBG, round(UAMpredBGs[UAMpredBGs.size - 1]))
            }

            // set eventualBG based on COB or UAM predBGs
            rT.eventualBG = eventualBG
        }

        consoleError.add("UAM Impact: $uci mg/dL per 5m; UAM Duration: $UAMduration hours")


        minIOBPredBG = max(39, minIOBPredBG)
        minCOBPredBG = max(39, minCOBPredBG)
        minUAMPredBG = max(39, minUAMPredBG)
        minPredBG = minIOBPredBG

        val fractionCarbsLeft = meal_data.mealCOB / meal_data.carbs
        // if we have COB and UAM is enabled, average both
        if (minUAMPredBG < 999 && minCOBPredBG < 999) {
            // weight COBpredBG vs. UAMpredBG based on how many carbs remain as COB
            avgPredBG = round((1 - fractionCarbsLeft) * UAMpredBG!! + fractionCarbsLeft * COBpredBG!!)
            // if UAM is disabled, average IOB and COB
        } else if (minCOBPredBG < 999) {
            avgPredBG = round((IOBpredBG + COBpredBG!!) / 2.0)
            // if we have UAM but no COB, average IOB and UAM
        } else if (minUAMPredBG < 999) {
            avgPredBG = round((IOBpredBG + UAMpredBG!!) / 2.0)
        } else {
            avgPredBG = round(IOBpredBG)
        }
        // if avgPredBG is below minZTGuardBG, bring it up to that level
        if (minZTGuardBG > avgPredBG) {
            avgPredBG = minZTGuardBG
        }

        // if we have both minCOBGuardBG and minUAMGuardBG, blend according to fractionCarbsLeft
        if ((cid > 0.0 || remainingCIpeak > 0)) {
            if (enableUAM) {
                minGuardBG = round(fractionCarbsLeft * minCOBGuardBG + (1 - fractionCarbsLeft) * minUAMGuardBG)
            } else {
                minGuardBG = round(minCOBGuardBG)
            }
        } else if (enableUAM) {
            minGuardBG = round(minUAMGuardBG)
        } else {
            minGuardBG = round(minIOBGuardBG)
        }
        //minGuardBG = round(minGuardBG)
        //console.error(minCOBGuardBG, minUAMGuardBG, minIOBGuardBG, minGuardBG);

        var minZTUAMPredBG: Int = minUAMPredBG
        // if minZTGuardBG is below threshold, bring down any super-high minUAMPredBG by averaging
        // this helps prevent UAM from giving too much insulin in case absorption falls off suddenly
        if (minZTGuardBG < threshold) {
            minZTUAMPredBG = round((minUAMPredBG + minZTGuardBG) / 2.0)
            // if minZTGuardBG is between threshold and target, blend in the averaging
        } else if (minZTGuardBG < target_bg) {
            // target 100, threshold 70, minZTGuardBG 85 gives 50%: (85-70) / (100-70)
            val blendPct = (minZTGuardBG - threshold) / (target_bg - threshold)
            val blendedMinZTGuardBG = minUAMPredBG * blendPct + minZTGuardBG * (1 - blendPct)
            minZTUAMPredBG = round((minUAMPredBG + blendedMinZTGuardBG) / 2)
            //minZTUAMPredBG = minUAMPredBG - target_bg + minZTGuardBG;
            // if minUAMPredBG is below minZTGuardBG, bring minUAMPredBG up by averaging
            // this allows more insulin if lastUAMPredBG is below target, but minZTGuardBG is still high
        } else if (minZTGuardBG > minUAMPredBG) {
            minZTUAMPredBG = round((minUAMPredBG + minZTGuardBG) / 2.0)
        }
        //console.error("minUAMPredBG:",minUAMPredBG,"minZTGuardBG:",minZTGuardBG,"minZTUAMPredBG:",minZTUAMPredBG);
        // if any carbs have been entered recently
        if (meal_data.carbs > 0) {

            // if UAM is disabled, use max of minIOBPredBG, minCOBPredBG
            if (!enableUAM && minCOBPredBG < 999) {
                minPredBG = max(minIOBPredBG, minCOBPredBG)
                // if we have COB, use minCOBPredBG, or blendedMinPredBG if it's higher
            } else if (minCOBPredBG < 999) {
                // calculate blendedMinPredBG based on how many carbs remain as COB
                val blendedMinPredBG = round(fractionCarbsLeft * minCOBPredBG + (1 - fractionCarbsLeft) * minZTUAMPredBG)
                // if blendedMinPredBG > minCOBPredBG, use that instead
                minPredBG = max(minIOBPredBG, max(minCOBPredBG, blendedMinPredBG))
                // if carbs have been entered, but have expired, use minUAMPredBG
            } else if (enableUAM) {
                minPredBG = minZTUAMPredBG
            } else {
                minPredBG = minGuardBG
            }
            // in pure UAM mode, use the higher of minIOBPredBG,minUAMPredBG
        } else if (enableUAM) {
            minPredBG = max(minIOBPredBG, minZTUAMPredBG)
        }
        // make sure minPredBG isn't higher than avgPredBG
        minPredBG = min(minPredBG, avgPredBG)

        consoleLog.add("minPredBG: $minPredBG minIOBPredBG: $minIOBPredBG minZTGuardBG: $minZTGuardBG")
        if (minCOBPredBG < 999) {
            consoleLog.add("minCOBPredBG: $minCOBPredBG")
        }
        if (minUAMPredBG < 999) {
            consoleLog.add("minUAMPredBG: $minUAMPredBG")
        }
        consoleError.add("avgPredBG: $avgPredBG COB: ${meal_data.mealCOB} / ${meal_data.carbs}")
        // But if the COB line falls off a cliff, don't trust UAM too much:
        // use maxCOBPredBG if it's been set and lower than minPredBG
        if (maxCOBPredBG > bg) {
            minPredBG = min(minPredBG, maxCOBPredBG.toInt())
        }

        rT.COB = meal_data.mealCOB
        rT.IOB = iob_data.iob
        rT.reason.append(
            "COB: ${round(meal_data.mealCOB, 1).withoutZeros()}, Dev: ${convert_bg(deviation.toDouble())}, BGI: ${convert_bg(bgi)}, ISF: ${convert_bg(sens)}, CR: ${
                round(profile.carb_ratio, 2)
                    .withoutZeros()
            }, Target: ${convert_bg(target_bg)}, minPredBG ${convert_bg(minPredBG)}, minGuardBG ${convert_bg(minGuardBG)}, IOBpredBG ${convert_bg(lastIOBpredBG)}"
        )
        if (lastCOBpredBG != null) {
            rT.reason.append(", COBpredBG " + convert_bg(lastCOBpredBG.toDouble()))
        }
        if (lastUAMpredBG != null) {
            rT.reason.append(", UAMpredBG " + convert_bg(lastUAMpredBG.toDouble()))
        }
        rT.reason.append("; ")
        // use naive_eventualBG if above 40, but switch to minGuardBG if both eventualBGs hit floor of 39
        var carbsReqBG = naive_eventualBG
        if (carbsReqBG < 40) {
            carbsReqBG = min(minGuardBG, carbsReqBG)
        }
        var bgUndershoot: Double = threshold - carbsReqBG
        // calculate how long until COB (or IOB) predBGs drop below min_bg
        var minutesAboveMinBG = 240
        var minutesAboveThreshold = 240
        if (meal_data.mealCOB > 0 && (ci > 0 || remainingCIpeak > 0)) {
            for (i in COBpredBGs.indices) {
                //console.error(COBpredBGs[i], min_bg);
                if (COBpredBGs[i] < min_bg) {
                    minutesAboveMinBG = 5 * i
                    break
                }
            }
            for (i in COBpredBGs.indices) {
                //console.error(COBpredBGs[i], threshold);
                if (COBpredBGs[i] < threshold) {
                    minutesAboveThreshold = 5 * i
                    break
                }
            }
        } else {
            for (i in IOBpredBGs.indices) {
                //console.error(IOBpredBGs[i], min_bg);
                if (IOBpredBGs[i] < min_bg) {
                    minutesAboveMinBG = 5 * i
                    break
                }
            }
            for (i in IOBpredBGs.indices) {
                //console.error(IOBpredBGs[i], threshold);
                if (IOBpredBGs[i] < threshold) {
                    minutesAboveThreshold = 5 * i
                    break
                }
            }
        }

        if (enableSMB && minGuardBG < threshold) {
            consoleError.add("minGuardBG ${convert_bg(minGuardBG.toDouble())} projected below ${convert_bg(threshold)} - disabling SMB")
            //rT.reason += "minGuardBG "+minGuardBG+"<"+threshold+": SMB disabled; ";
            enableSMB = false
        }
        var maxDeltaPercentage = 0.2                        // the AAPS default
        if ( loop_wanted_smb == "fullLoop" ) {              // only if SMB specifically requested, e.g. for full loop
            maxDeltaPercentage = 0.3
        }
        if ( maxDelta > maxDeltaPercentage * bg ) {
            consoleError.add("maxDelta ${convert_bg(maxDelta)} > $maxDeltaPercentage% of BG ${convert_bg(bg)} - disabling SMB")
            rT.reason.append("maxDelta " + convert_bg(maxDelta) + " > " + maxDeltaPercentage + "% of BG " + convert_bg(bg) + ": SMB disabled; ")
            enableSMB = false
        }

        consoleError.add("BG projected to remain above ${convert_bg(min_bg)} for $minutesAboveMinBG minutes")
        if (minutesAboveThreshold < 240 || minutesAboveMinBG < 60) {
            consoleError.add("BG projected to remain above ${convert_bg(threshold)} for $minutesAboveThreshold minutes")
        }
        // include at least minutesAboveThreshold worth of zero temps in calculating carbsReq
        // always include at least 30m worth of zero temp (carbs to 80, low temp up to target)
        val zeroTempDuration = minutesAboveThreshold
        // BG undershoot, minus effect of zero temps until hitting min_bg, converted to grams, minus COB
        val zeroTempEffectDouble = profile.current_basal * sens * zeroTempDuration / 60
        // don't count the last 25% of COB against carbsReq
        val COBforCarbsReq = max(0.0, meal_data.mealCOB - 0.25 * meal_data.carbs)
        val carbsReq = round(((bgUndershoot - zeroTempEffectDouble) / csf - COBforCarbsReq))
        val zeroTempEffect = round(zeroTempEffectDouble)
        consoleError.add("naive_eventualBG: $naive_eventualBG bgUndershoot: $bgUndershoot zeroTempDuration $zeroTempDuration zeroTempEffect: $zeroTempEffect carbsReq: $carbsReq")
        if (carbsReq >= profile.carbsReqThreshold && minutesAboveThreshold <= 45) {
            rT.carbsReq = carbsReq
            rT.carbsReqWithin = minutesAboveThreshold
            rT.reason.append("$carbsReq add\'l carbs req w/in ${minutesAboveThreshold}m; ")
        }

        // don't low glucose suspend if IOB is already super negative and BG is rising faster than predicted
        if (bg < threshold && iob_data.iob < -profile.current_basal * 20 / 60 && minDelta > 0 && minDelta > expectedDelta) {
            rT.reason.append("IOB ${iob_data.iob} < ${round(-profile.current_basal * 20 / 60, 2)}")
            rT.reason.append(" and minDelta ${convert_bg(minDelta)} > expectedDelta ${convert_bg(expectedDelta)}; ")
            // predictive low glucose suspend mode: BG is / is projected to be < threshold
        } else if (bg < threshold || minGuardBG < threshold) {
            rT.reason.append("minGuardBG " + convert_bg(minGuardBG.toDouble()) + "<" + convert_bg(threshold))
            bgUndershoot = (target_bg - minGuardBG)
            val worstCaseInsulinReq = bgUndershoot / sens
            var durationReq = round(60 * worstCaseInsulinReq / profile.current_basal)
            durationReq = round(durationReq / 30.0) * 30
            // always set a 30-120m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
            durationReq = min(120, max(30, durationReq))
            return setTempBasal(0.0, durationReq, profile, rT, currenttemp)
        }

        // if not in LGS mode, cancel temps before the top of the hour to reduce beeping/vibration
        // console.error(profile.skip_neutral_temps, rT.deliverAt.getMinutes());
        val minutes = Instant.ofEpochMilli(rT.deliverAt!!).atZone(ZoneId.systemDefault()).toLocalDateTime().minute
        if (profile.skip_neutral_temps && minutes >= 55) {
            rT.reason.append("; Canceling temp at " + minutes + "m past the hour. ")
            return setTempBasal(0.0, 0, profile, rT, currenttemp)
        }

        if (eventualBG < min_bg) { // if eventual BG is below target:
            rT.reason.append("Eventual BG ${convert_bg(eventualBG.toDouble())} < ${convert_bg(min_bg)}")
            // if 5m or 30m avg BG is rising faster than expected delta
            if (minDelta > expectedDelta && minDelta > 0 && carbsReq == 0) {
                // if naive_eventualBG < 40, set a 30m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
                if (naive_eventualBG < 40) {
                    rT.reason.append(", naive_eventualBG < 40. ")
                    return setTempBasal(0.0, 30, profile, rT, currenttemp)
                }
                if (glucose_status.delta > minDelta) {
                    rT.reason.append(", but Delta ${convert_bg(tick.toDouble())} > expectedDelta ${convert_bg(expectedDelta)}")
                } else {
                    rT.reason.append(", but Min. Delta ${minDelta.toFixed2()} > Exp. Delta ${convert_bg(expectedDelta)}")
                }
                if (currenttemp.duration > 15 && (round_basal(basal) == round_basal(currenttemp.rate))) {
                    rT.reason.append(", temp " + currenttemp.rate + " ~ req " + round(basal, 2).withoutZeros() + "U/hr. ")
                    return rT
                } else {
                    rT.reason.append("; setting current basal of ${round(basal, 2)} as temp. ")
                    return setTempBasal(basal, 30, profile, rT, currenttemp)
                }
            }

            // calculate 30m low-temp required to get projected BG up to target
            // multiply by 2 to low-temp faster for increased hypo safety
            var insulinReq = 2 * min(0.0, (eventualBG - target_bg) / sens)
            insulinReq = round(insulinReq, 2)
            // calculate naiveInsulinReq based on naive_eventualBG
            var naiveInsulinReq = min(0.0, (naive_eventualBG - target_bg) / sens)
            naiveInsulinReq = round(naiveInsulinReq, 2)
            if (minDelta < 0 && minDelta > expectedDelta) {
                // if we're barely falling, newinsulinReq should be barely negative
                val newinsulinReq = round((insulinReq * (minDelta / expectedDelta)), 2)
                //console.error("Increasing insulinReq from " + insulinReq + " to " + newinsulinReq);
                insulinReq = newinsulinReq
            }
            // rate required to deliver insulinReq less insulin over 30m:
            var rate = basal + (2 * insulinReq)
            rate = round_basal(rate)

            // if required temp < existing temp basal
            val insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60
            // if current temp would deliver a lot (30% of basal) less than the required insulin,
            // by both normal and naive calculations, then raise the rate
            val minInsulinReq = Math.min(insulinReq, naiveInsulinReq)
            if (insulinScheduled < minInsulinReq - basal * 0.3) {
                rT.reason.append(", ${currenttemp.duration}m@${(currenttemp.rate).toFixed2()} is a lot less than needed. ")
                return setTempBasal(rate, 30, profile, rT, currenttemp)
            }
            if (currenttemp.duration > 5 && rate >= currenttemp.rate * 0.8) {
                rT.reason.append(", temp ${currenttemp.rate} ~< req ${round(rate, 2)}U/hr. ")
                return rT
            } else {
                // calculate a long enough zero temp to eventually correct back up to target
                if (rate <= 0) {
                    bgUndershoot = (target_bg - naive_eventualBG)
                    val worstCaseInsulinReq = bgUndershoot / sens
                    var durationReq = round(60 * worstCaseInsulinReq / profile.current_basal)
                    if (durationReq < 0) {
                        durationReq = 0
                        // don't set a temp longer than 120 minutes
                    } else {
                        durationReq = round(durationReq / 30.0) * 30
                        durationReq = min(120, max(0, durationReq))
                    }
                    //console.error(durationReq);
                    if (durationReq > 0) {
                        rT.reason.append(", setting ${durationReq}m zero temp. ")
                        return setTempBasal(rate, durationReq, profile, rT, currenttemp)
                    }
                } else {
                    rT.reason.append(", setting ${round(rate, 2)}U/hr. ")
                }
                return setTempBasal(rate, 30, profile, rT, currenttemp)
            }
        }

        // if eventual BG is above min but BG is falling faster than expected Delta
        if (minDelta < expectedDelta) {
            // if in SMB mode, don't cancel SMB zero temp
            if (!(microBolusAllowed && enableSMB)) {
                if (glucose_status.delta < minDelta) {
                    rT.reason.append(
                        "Eventual BG ${convert_bg(eventualBG)} > ${convert_bg(min_bg)} but Delta ${convert_bg(tick.toDouble())} < Exp. Delta ${
                            convert_bg(expectedDelta)
                        }"
                    )
                } else {
                    rT.reason.append("Eventual BG ${convert_bg(eventualBG)} > ${convert_bg(min_bg)} but Min. Delta ${minDelta.toFixed2()} < Exp. Delta ${convert_bg(expectedDelta)}")
                }
                if (currenttemp.duration > 15 && (round_basal(basal) == round_basal(currenttemp.rate))) {
                    rT.reason.append(", temp " + currenttemp.rate + " ~ req " + round(basal, 2).withoutZeros() + "U/hr. ")
                    return rT
                } else {
                    rT.reason.append("; setting current basal of ${round(basal, 2)} as temp. ")
                    return setTempBasal(basal, 30, profile, rT, currenttemp)
                }
            }
        }
        // eventualBG or minPredBG is below max_bg
        if (Math.min(eventualBG, minPredBG) < max_bg) {
            // if in SMB mode, don't cancel SMB zero temp
            if (!(microBolusAllowed && enableSMB)) {
                rT.reason.append("${convert_bg(eventualBG.toDouble())}-${convert_bg(minPredBG.toDouble())} in range: no temp required")
                if (currenttemp.duration > 15 && (round_basal(basal) == round_basal(currenttemp.rate))) {
                    rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2).withoutZeros()}U/hr. ")
                    return rT
                } else {
                    rT.reason.append("; setting current basal of ${round(basal, 2)} as temp. ")
                    return setTempBasal(basal, 30, profile, rT, currenttemp)
                }
            }
        }

        // eventual BG is at/above target
        // if iob is over max, just cancel any temps
        if (eventualBG >= max_bg) {
            rT.reason.append("Eventual BG " + convert_bg(eventualBG.toDouble()) + " >= " + convert_bg(max_bg) + ", ")
        }
        if (iob_data.iob > max_iob) {
            rT.reason.append("IOB ${round(iob_data.iob, 2)} > max_iob $max_iob")
            if (currenttemp.duration > 15 && (round_basal(basal) == round_basal(currenttemp.rate))) {
                rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2).withoutZeros()}U/hr. ")
                return rT
            } else {
                rT.reason.append("; setting current basal of ${round(basal, 2)} as temp. ")
                return setTempBasal(basal, 30, profile, rT, currenttemp)
            }
        } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

            // insulinReq is the additional insulin required to get minPredBG down to target_bg
            //console.error(minPredBG,eventualBG);
            var insulinReq = round((min(minPredBG, eventualBG) - target_bg) / sens, 2)
            // if that would put us over max_iob, then reduce accordingly
            if (insulinReq > max_iob - iob_data.iob) {
                rT.reason.append("max_iob $max_iob, ")
                consoleLog.add("InsReq ${round(insulinReq,2)} capped at ${round(max_iob-iob_data.iob,2)} to not exceed max_iob $max_iob")
                insulinReq = max_iob - iob_data.iob
            }

            // rate required to deliver insulinReq more insulin over 30m:
            var rate = basal + (2 * insulinReq)
            rate = round_basal(rate)
            insulinReq = round(insulinReq, 3)
            rT.insulinReq = insulinReq
            //console.error(iob_data.lastBolusTime);
            // minutes since last bolus
            val lastBolusAge = round((systemTime - iob_data.lastBolusTime) / 60000.0, 1)
            //console.error(lastBolusAge);
            //console.error(profile.temptargetSet, target_bg, rT.COB);
            // only allow microboluses with COB or low temp targets, or within DIA hours of a bolus
            val maxBolus: Double
            if (microBolusAllowed && enableSMB && bg > threshold) {
                // never bolus more than maxSMBBasalMinutes worth of basal
                val mealInsulinReq = round(meal_data.mealCOB / profile.carb_ratio, 3)
                val smb_max_range = profile.smb_max_range_extension!!
                if (iob_data.iob > mealInsulinReq && iob_data.iob > 0) {
                    consoleError.add("IOB ${iob_data.iob} > COB ${meal_data.mealCOB}; mealInsulinReq = $mealInsulinReq")
                    consoleError.add("profile.maxUAMSMBBasalMinutes: ${profile.maxUAMSMBBasalMinutes} profile.current_basal: ${profile.current_basal}")
                    maxBolus = round(smb_max_range * profile.current_basal * profile.maxUAMSMBBasalMinutes / 60, 1)
                } else {
                    consoleError.add("profile.maxSMBBasalMinutes: ${profile.maxSMBBasalMinutes} profile.current_basal: ${profile.current_basal}")
                    maxBolus = round(smb_max_range * profile.current_basal * profile.maxSMBBasalMinutes / 60, 1)
                }
                // bolus 1/2 the insulinReq, up to maxBolus, rounding down to nearest bolus increment
                val roundSMBTo = 1 / profile.bolus_increment
                var smb_ratio = determine_varSMBratio(profile, bg.toInt(), target_bg, loop_wanted_smb)

                var microBolus = Math.min(insulinReq*smb_ratio, maxBolus)
                // mod autoISF3.0-dev: if that would put us over iobTH, then reduce accordingly; allow 30% overrun
                val iobTHtolerance = 130
                val iobTHvirtual = profile.iob_threshold_percent!!*iobTHtolerance/10000 * profile.max_iob * iobTH_reduction_ratio
                if (microBolus > iobTHvirtual - iob_data.iob && (loop_wanted_smb=="fullLoop" || loop_wanted_smb=="enforced")) {
                    microBolus = iobTHvirtual - iob_data.iob
                    consoleLog.add("Full loop capped SMB at ${round(microBolus,2)} to not exceed $iobTHtolerance% of effective iobTH ${round(iobTHvirtual/iobTHtolerance*100,2)}U")
                }
                microBolus = Math.floor(microBolus*roundSMBTo)/roundSMBTo
                // calculate a long enough zero temp to eventually correct back up to target
                val smbTarget = target_bg
                val worstCaseInsulinReq = (smbTarget - (naive_eventualBG + minIOBPredBG) / 2.0) / sens
                var durationReq = round(60 * worstCaseInsulinReq / profile.current_basal)

                // if insulinReq > 0 but not enough for a microBolus, don't set an SMB zero temp
                if (insulinReq > 0 && microBolus < profile.bolus_increment) {
                    durationReq = 0
                }

                var smbLowTempReq = 0.0
                if (durationReq <= 0) {
                    durationReq = 0
                    // don't set an SMB zero temp longer than 60 minutes
                } else if (durationReq >= 30) {
                    durationReq = round(durationReq / 30.0) * 30
                    durationReq = min(60, max(0, durationReq))
                } else {
                    // if SMB durationReq is less than 30m, set a nonzero low temp
                    smbLowTempReq = round(basal * durationReq / 30.0, 2)
                    durationReq = 30
                }
                rT.reason.append(" insulinReq $insulinReq")
                if (microBolus >= maxBolus) {
                    rT.reason.append("; maxBolus $maxBolus")
                }
                if (durationReq > 0) {
                    rT.reason.append("; setting ${durationReq}m low temp of ${smbLowTempReq}U/h")
                }
                rT.reason.append(". ")

                // allow SMBIntervals between 1 and 10 minutes
                val SMBInterval = min(10, max(1, profile.SMBInterval))
                val nextBolusMins = round(SMBInterval - lastBolusAge, 0)
                val nextBolusSeconds = round((SMBInterval - lastBolusAge) * 60, 0) % 60
                //console.error(naive_eventualBG, insulinReq, worstCaseInsulinReq, durationReq);
                consoleError.add("naive_eventualBG $naive_eventualBG,${durationReq}m ${smbLowTempReq}U/h temp needed; last bolus ${lastBolusAge}m ago; maxBolus: $maxBolus")
                if (lastBolusAge > SMBInterval) {
                    if (microBolus > 0) {
                        rT.units = microBolus
                        rT.reason.append("Microbolusing ${microBolus}U. ")
                    }
                } else {
                    rT.reason.append("Waiting " + nextBolusMins + "m " + nextBolusSeconds + "s to microbolus again. ")
                }
                //rT.reason += ". ";

                // if no zero temp is required, don't return yet; allow later code to set a high temp
                if (durationReq > 0) {
                    rT.rate = smbLowTempReq
                    rT.duration = durationReq
                    return rT
                }

            }

            val maxSafeBasal = getMaxSafeBasal(profile)

            if (rate > maxSafeBasal) {
                rT.reason.append("adj. req. rate: ${round(rate, 2)} to maxSafeBasal: ${maxSafeBasal.withoutZeros()}, ")
                rate = round_basal(maxSafeBasal)
            }

            val insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60
            if (insulinScheduled >= insulinReq * 2) { // if current temp would deliver >2x more than the required insulin, lower the rate
                rT.reason.append("${currenttemp.duration}m@${(currenttemp.rate).toFixed2()} > 2 * insulinReq. Setting temp basal of ${round(rate, 2)}U/hr. ")
                return setTempBasal(rate, 30, profile, rT, currenttemp)
            }

            if (currenttemp.duration == 0) { // no temp is set
                rT.reason.append("no temp, setting " + round(rate, 2).withoutZeros() + "U/hr. ")
                return setTempBasal(rate, 30, profile, rT, currenttemp)
            }

            if (currenttemp.duration > 5 && (round_basal(rate) <= round_basal(currenttemp.rate))) { // if required temp <~ existing temp basal
                rT.reason.append("temp ${(currenttemp.rate).toFixed2()} >~ req ${round(rate, 2).withoutZeros()}U/hr. ")
                return rT
            }

            // required temp > existing temp basal
            rT.reason.append("temp ${currenttemp.rate.toFixed2()} < ${round(rate, 2).withoutZeros()}U/hr. ")
            return setTempBasal(rate, 30, profile, rT, currenttemp)
        }
    }
}
