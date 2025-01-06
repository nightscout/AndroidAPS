package app.aaps.plugins.aps.openAPSAMA

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.profile.ProfileUtil
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Singleton
class DetermineBasalAMA @Inject constructor(
    private val profileUtil: ProfileUtil
) {

    private val consoleError = mutableListOf<String>()
    private val consoleLog = mutableListOf<String>()

    private fun Double.toFixed2(): String = DecimalFormat("0.00#").format(round(this, 2))
    private fun Double.toFixed3(): String = DecimalFormat("0.000#").format(round(this, 3))

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
    fun calculate_expected_delta(dia: Double, targetBg: Double, eventualBg: Double, bgi: Double): Double {
        // (hours * mins_per_hour) / 5 = how many 5 minute periods in 2h = 24
        val dia_in_5min_blocks = (dia / 2.0 * 60.0) / 5.0
        val target_delta = targetBg - eventualBg
        val expectedDelta = round(bgi + (target_delta / dia_in_5min_blocks), 1)
        return expectedDelta
    }

    fun convert_bg(value: Double): String =
        profileUtil.fromMgdlToStringInUnits(value).replace("-0.0", "0.0")

    fun reason(rT: RT, msg: String) {
        if (rT.reason.toString().isNotEmpty()) rT.reason.append(". ")
        rT.reason.append(msg)
        consoleError.add(msg)
    }

    private fun getMaxSafeBasal(profile: OapsProfile): Double =
        min(profile.max_basal, min(profile.max_daily_safety_multiplier * profile.max_daily_basal, profile.current_basal_safety_multiplier * profile.current_basal))

    fun setTempBasal(_rate: Double, duration: Int, profile: OapsProfile, rT: RT, currenttemp: CurrentTemp): RT {
        //var maxSafeBasal = Math.min(profile.max_basal, 3 * profile.max_daily_basal, 4 * profile.current_basal);

        val maxSafeBasal = getMaxSafeBasal(profile)
        var rate = _rate
        if (rate < 0) rate = 0.0
        else if (rate > maxSafeBasal) rate = maxSafeBasal

        val suggestedRate = round_basal(rate)
        if (currenttemp.duration > (duration - 10) && currenttemp.duration <= 120 && suggestedRate <= currenttemp.rate * 1.2 && suggestedRate >= currenttemp.rate * 0.8 && duration > 0) {
            rT.reason.append(", but ${currenttemp.duration}m left and ${currenttemp.rate.withoutZeros()} ~ req ${suggestedRate.withoutZeros()}U/hr: no action required")
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

    fun determine_basal(
        glucose_status: GlucoseStatus, currenttemp: CurrentTemp, iob_data_array: Array<IobTotal>, profile: OapsProfile, autosens_data: AutosensResult, meal_data: MealData, currentTime: Long
    ): RT {
        consoleError.clear()
        consoleLog.clear()
        var rT = RT(
            algorithm = APSResult.Algorithm.AMA,
            runningDynamicIsf = false,
            timestamp = currentTime,
            consoleLog = consoleLog,
            consoleError = consoleError
        )
        val basal = round_basal(profile.current_basal * autosens_data.ratio)
        if (basal != profile.current_basal) {
            consoleError.add("Adjusting basal from ${profile.current_basal} to $basal")
        }

        val bg = glucose_status.glucose
        // TODO: figure out how to use raw isig data to estimate BG
        if (bg < 39) {  //Dexcom is in ??? mode or calibrating
            rT.reason.append("CGM is calibrating or in ??? state")
            if (basal <= currenttemp.rate * 1.2) { // high temp is running
                rT.reason.append("; setting current basal of ${round(basal, 2)} as temp")
                return setTempBasal(basal, 30, profile, rT, currenttemp)
            } else { //do nothing.
                rT.reason.append(", temp ${currenttemp.rate} <~ current basal ${round(basal, 2)}U/hr")
                return rT
            }
        }

        val max_iob = profile.max_iob // maximum amount of non-bolus IOB OpenAPS will ever deliver

        // if target_bg is set, great. otherwise, if min and max are set, then set target to their average
        var target_bg = profile.target_bg
        var min_bg = profile.min_bg
        var max_bg = profile.max_bg

        // adjust min, max, and target BG for sensitivity, such that 50% increase in ISF raises target from 100 to 120
        if (profile.autosens_adjust_targets) {
            if (profile.temptargetSet) {
                consoleError.add("Temp Target set, not adjusting with autosens")
            } else {
                min_bg = round((min_bg - 60) / autosens_data.ratio, 0) + 60
                max_bg = round((max_bg - 60) / autosens_data.ratio, 0) + 60
                val new_target_bg = round((target_bg - 60) / autosens_data.ratio) + 60.0
                if (target_bg == new_target_bg) {
                    consoleError.add("target_bg unchanged: $new_target_bg")
                } else {
                    consoleError.add("Adjusting target_bg from $target_bg to $new_target_bg")
                }
                target_bg = new_target_bg
            }
        }

        val iobArray = iob_data_array
        val iob_data = iobArray[0]

        val tick: String

        if (glucose_status.delta > -0.5) {
            tick = "+" + round(glucose_status.delta)
        } else {
            tick = round(glucose_status.delta).toString()
        }
        val minDelta = min(glucose_status.delta, min(glucose_status.shortAvgDelta, glucose_status.longAvgDelta))
        val minAvgDelta = min(glucose_status.shortAvgDelta, glucose_status.longAvgDelta)

        val sens = round(profile.sens / autosens_data.ratio, 1)
        if (sens != profile.sens) {
            consoleError.add("Adjusting sens from ${profile.sens} to $sens")
        }

        //calculate BG impact: the amount BG "should" be rising or falling based on insulin activity alone
        val bgi = round((-iob_data.activity * sens * 5), 2)
        // project deviations for 30 minutes
        var deviation = round(30 / 5 * (minDelta - bgi))
        // don't overreact to a big negative delta: use minAvgDelta if deviation is negative
        if (deviation < 0) {
            deviation = round((30 / 5) * (minAvgDelta - bgi))
        }

        // calculate the naive (bolus calculator math) eventual BG based on net IOB and sensitivity
        val naive_eventualBG =
            if (iob_data.iob > 0) round(bg - (iob_data.iob * sens), 0)
            else  // if IOB is negative, be more conservative and use the lower of sens, profile.sens
                round(bg - (iob_data.iob * min(sens, profile.sens)), 0)

        // and adjust it for the deviation above
        var eventualBG = naive_eventualBG + deviation
        // calculate what portion of that is due to bolussnooze
        val bolusContrib = iob_data.bolussnooze * sens
        // and add it back in to get snoozeBG, plus another 50% to avoid low-temping at mealtime
        val naive_snoozeBG = round(naive_eventualBG + 1.5 * bolusContrib, 0)
        // adjust that for deviation like we did eventualBG
        var snoozeBG = naive_snoozeBG + deviation

        val expectedDelta = calculate_expected_delta(profile.dia, target_bg, eventualBG, bgi)

        // min_bg of 90 -> threshold of 70, 110 -> 80, and 130 -> 90
        val threshold = min_bg - 0.5 * (min_bg - 50)

        rT = RT(
            algorithm = APSResult.Algorithm.AMA,
            runningDynamicIsf = false,
            timestamp = currentTime,
            bg = bg,
            tick = tick,
            eventualBG = eventualBG,
            snoozeBG = snoozeBG,
            consoleLog = consoleLog,
            consoleError = consoleError
        )

        val basaliob = iob_data.basaliob

        // generate predicted future BGs based on IOB, COB, and current absorption rate

        var COBpredBGs = mutableListOf<Double>()
        var aCOBpredBGs = mutableListOf<Double>()
        var IOBpredBGs = mutableListOf<Double>()
        COBpredBGs.add(bg)
        aCOBpredBGs.add(bg)
        IOBpredBGs.add(bg)
        //console.error(meal_data);
        // carb impact and duration are 0 unless changed below
        var ci: Double
        val cid: Double
        // calculate current carb absorption rate, and how long to absorb all carbs
        // CI = current carb impact on BG in mg/dL/5m
        ci = round((minDelta - bgi) * 10) / 10.0
        if (meal_data.mealCOB * 2 > meal_data.carbs) {
            // set ci to a minimum of 3mg/dL/5m (default) if less than half of carbs have absorbed
            ci = max(profile.min_5m_carbimpact, ci)
        }
        val aci = 10
        //5m data points = g * (1U/10g) * (40mg/dL/1U) / (mg/dL/5m)
        cid = meal_data.mealCOB * (sens / profile.carb_ratio) / ci
        val acid: Double = meal_data.mealCOB * (sens / profile.carb_ratio) / aci
        consoleError.add("Carb Impact: $ci mg/dL per 5m; CI Duration: ${Math.round(10 * cid / 6) / 10} hours")
        consoleError.add("Accel. Carb Impact: $aci mg/dL per 5m; ACI Duration: ${Math.round(10 * acid / 6) / 10} hours")
        var minPredBG = 999.0
        var maxPredBG = bg
        //var eventualPredBG = bg
        var IOBpredBG: Double
        var COBpredBG: Double
        var aCOBpredBG: Double
        iobArray.forEach { iobTick ->
            //console.error(iobTick);
            val predBGI: Double = round((-iobTick.activity * sens * 5), 2)
            // predicted deviation impact drops linearly from current deviation down to zero
            // over 60 minutes (data points every 5m)
            val predDev: Double = (ci * (1 - min(1, IOBpredBGs.size / (60 / 5))))
            IOBpredBG = IOBpredBGs[IOBpredBGs.size - 1] + predBGI + predDev
            //IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + predBGI;
            // predicted carb impact drops linearly from current carb impact down to zero
            // eventually accounting for all carbs (if they can be absorbed over DIA)
            val predCI: Double = max(0.0, ci * (1 - COBpredBGs.size / max(cid * 2, 1.0)))
            val predACI = max(0.0, aci * (1 - COBpredBGs.size / max(acid * 2, 1.0)))
            COBpredBG = COBpredBGs[COBpredBGs.size - 1] + predBGI + min(0.0, predDev) + predCI
            aCOBpredBG = aCOBpredBGs[aCOBpredBGs.size - 1] + predBGI + min(0.0, predDev) + predACI
            //console.error(predBGI, predCI, predBG);
            IOBpredBGs.add(IOBpredBG)
            COBpredBGs.add(COBpredBG)
            aCOBpredBGs.add(aCOBpredBG)
            // wait 45m before setting minPredBG
            if (COBpredBGs.size > 9 && (COBpredBG < minPredBG)) {
                minPredBG = COBpredBG
            }
            if (COBpredBG > maxPredBG) {
                maxPredBG = COBpredBG
            }
        }
        // set eventualBG to include effect of carbs
        //console.error("PredBGs:",JSON.stringify(predBGs));
        rT.predBGs = Predictions()
        IOBpredBGs = IOBpredBGs.map { min(401.0, max(39.0, it)) }.toMutableList()
        for (i in IOBpredBGs.size - 1 downTo 13) {
            if (IOBpredBGs[i - 1] != IOBpredBGs[i]) break
            else IOBpredBGs.removeAt(IOBpredBGs.lastIndex)
        }
        rT.predBGs?.IOB = IOBpredBGs.map { it.toInt() }
        if (meal_data.mealCOB > 0) {
            aCOBpredBGs = aCOBpredBGs.map { min(401.0, max(39.0, it)) }.toMutableList()
            for (i in aCOBpredBGs.size - 1 downTo 13) {
                if (aCOBpredBGs[i - 1] != aCOBpredBGs[i]) break
                else aCOBpredBGs.removeAt(aCOBpredBGs.lastIndex)
            }
            rT.predBGs?.aCOB = aCOBpredBGs.map { it.toInt() }
        }
        if (meal_data.mealCOB > 0 && ci > 0) {
            COBpredBGs = COBpredBGs.map { min(401.0, max(39.0, it)) }.toMutableList()
            for (i in COBpredBGs.size - 1 downTo 13) {
                if (COBpredBGs[i - 1] != COBpredBGs[i]) break
                else COBpredBGs.removeAt(COBpredBGs.lastIndex)
            }
            rT.predBGs?.COB = COBpredBGs.map { it.toInt() }
            eventualBG = max(eventualBG, round(COBpredBGs[COBpredBGs.size - 1], 0))
            rT.eventualBG = eventualBG
            minPredBG = min(minPredBG, eventualBG)
            // set snoozeBG to minPredBG
            snoozeBG = round(max(snoozeBG, minPredBG), 0)
            rT.snoozeBG = snoozeBG
        }

        rT.COB = meal_data.mealCOB
        rT.IOB = iob_data.iob
        rT.reason.append("COB: ${round(meal_data.mealCOB, 1).withoutZeros()}, Dev: $deviation, BGI: ${bgi.withoutZeros()}, ISF: ${convert_bg(sens)}, Target: ${convert_bg(target_bg)}; ")
        if (profile.autosens_adjust_targets && autosens_data.ratio != 1.0)
            rT.reason.append("Autosens: " + autosens_data.ratio + "; ")
        if (bg < threshold) { // low glucose suspend mode: BG is < ~80
            rT.reason.append("BG ${convert_bg(bg)}<${convert_bg(threshold)}")
            if ((glucose_status.delta <= 0 && minDelta <= 0) || (glucose_status.delta < expectedDelta && minDelta < expectedDelta) || bg < 60) {
                // BG is still falling / rising slower than predicted
                return setTempBasal(0.0, 30, profile, rT, currenttemp)
            }
            if (glucose_status.delta > minDelta) {
                rT.reason.append(", delta ${glucose_status.delta}>0")
            } else {
                rT.reason.append(", min delta ${minDelta.toFixed2()}>0")
            }
            if (currenttemp.duration > 15 && round_basal(basal) == round_basal(currenttemp.rate)) {
                rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2)}U/hr")
                return rT
            } else {
                rT.reason.append("; setting current basal of " + round(basal, 2) + " as temp")
                return setTempBasal(basal, 30, profile, rT, currenttemp)
            }
        }

        if (eventualBG < min_bg) { // if eventual BG is below target:
            rT.reason.append("Eventual BG ${convert_bg(eventualBG)} < ${convert_bg(min_bg)}")
            // if 5m or 30m avg BG is rising faster than expected delta
            if (minDelta > expectedDelta && minDelta > 0) {
                if (glucose_status.delta > minDelta) {
                    rT.reason.append(", but Delta $tick > Exp. Delta $expectedDelta")
                } else {
                    rT.reason.append(", but Min. Delta " + minDelta.toFixed2() + " > Exp. Delta " + expectedDelta)
                }
                if (currenttemp.duration > 15 && round_basal(basal) == round_basal(currenttemp.rate)) {
                    rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2)}U/hr")
                    return rT
                } else {
                    rT.reason.append("; setting current basal of ${round(basal, 2)} as temp")
                    return setTempBasal(basal, 30, profile, rT, currenttemp)
                }
            }

            if (eventualBG < min_bg) {
                // if we've bolused recently, we can snooze until the bolus IOB decays (at double speed)
                if (snoozeBG > min_bg) { // if adding back in the bolus contribution BG would be above min
                    rT.reason.append(", bolus snooze: eventual BG range ${convert_bg(eventualBG)}-${convert_bg(snoozeBG)}")
                    //console.error(currenttemp, basal );
                    if (currenttemp.duration > 15 && round_basal(basal) == round_basal(currenttemp.rate)) {
                        rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2)}U/hr")
                        return rT
                    } else {
                        rT.reason.append("; setting current basal of ${round(basal, 2)} as temp")
                        return setTempBasal(basal, 30, profile, rT, currenttemp)
                    }
                } else {
                    // calculate 30m low-temp required to get projected BG up to target
                    // use snoozeBG to more gradually ramp in any counteraction of the user's boluses
                    // multiply by 2 to low-temp faster for increased hypo safety
                    var insulinReq = 2 * min(0.0, (snoozeBG - target_bg) / sens)
                    insulinReq = round(insulinReq, 2)
                    if (minDelta < 0 && minDelta > expectedDelta) {
                        // if we're barely falling, newinsulinReq should be barely negative
                        rT.reason.append(", Snooze BG ${convert_bg(snoozeBG)}")
                        val newinsulinReq = round((insulinReq * (minDelta / expectedDelta)), 2)
                        //console.error("Increasing insulinReq from " + insulinReq + " to " + newinsulinReq);
                        insulinReq = newinsulinReq
                    }
                    // rate required to deliver insulinReq less insulin over 30m:
                    var rate = basal + (2 * insulinReq)
                    rate = round_basal(rate)
                    // if required temp < existing temp basal
                    val insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60
                    if (insulinScheduled < insulinReq - basal * 0.3) { // if current temp would deliver a lot (30% of basal) less than the required insulin, raise the rate
                        rT.reason.append(", ${currenttemp.duration}m@${(currenttemp.rate - basal).toFixed3()} = ${insulinScheduled.toFixed3()} < req $insulinReq-${(basal * 0.3).toFixed2()}")
                        return setTempBasal(rate, 30, profile, rT, currenttemp)
                    }
                    if (currenttemp.duration > 5 && rate >= currenttemp.rate * 0.8) {
                        rT.reason.append(", temp ${(currenttemp.rate).toFixed3()} ~< req ${round(rate, 2)}U/hr")
                        return rT
                    } else {
                        rT.reason.append(", setting ${round(rate, 2).withoutZeros()}U/hr")
                        return setTempBasal(rate, 30, profile, rT, currenttemp)
                    }
                }
            }
        }

        val minutes_running: Int =
            if (currenttemp.duration == 0) 30
            else if (currenttemp.minutesrunning != null)
            // If the time the current temp is running is not defined, use default request duration of 30 minutes.
                currenttemp.minutesrunning!!
            else 30 - currenttemp.duration

        // if there is a low-temp running, and eventualBG would be below min_bg without it, let it run
        if (round_basal(currenttemp.rate) < round_basal(basal)) {
            val lowtempimpact = (currenttemp.rate - basal) * ((30 - minutes_running) / 60.0) * sens
            val adjEventualBG = eventualBG + lowtempimpact
            if (adjEventualBG < min_bg) {
                rT.reason.append("letting low temp of ${currenttemp.rate.withoutZeros()} run.")
                return rT
            }
        }

        // if eventual BG is above min but BG is falling faster than expected Delta
        if (minDelta < expectedDelta) {
            if (glucose_status.delta < minDelta) {
                rT.reason.append("Eventual BG ${convert_bg(eventualBG)} > ${convert_bg(min_bg)} but Delta $tick < Exp. Delta ${expectedDelta.withoutZeros()}")
            } else {
                rT.reason.append("Eventual BG ${convert_bg(eventualBG)} > ${convert_bg(min_bg)} but Min. Delta ${minDelta.toFixed2()} < Exp. Delta ${expectedDelta.withoutZeros()}")
            }
            if (currenttemp.duration > 15 && round_basal(basal) == round_basal(currenttemp.rate)) {
                rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2).withoutZeros()}U/hr")
                return rT
            } else {
                rT.reason.append("; setting current basal of " + round(basal, 2).withoutZeros() + " as temp")
                return setTempBasal(basal, 30, profile, rT, currenttemp)
            }
        }
        // eventualBG or snoozeBG (from minPredBG) is below max_bg
        if (eventualBG < max_bg || snoozeBG < max_bg) {
            // if there is a high-temp running and eventualBG > max_bg, let it run
            if (eventualBG > max_bg && round_basal(currenttemp.rate) > round_basal(basal)) {
                rT.reason.append(", $eventualBG > $max_bg: no action required (letting high temp of ${currenttemp.rate} run).")
                return rT
            }

            rT.reason.append("${convert_bg(eventualBG)}-${convert_bg(snoozeBG)} in range: no temp required")
            if (currenttemp.duration > 15 && round_basal(basal) == round_basal(currenttemp.rate)) {
                rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2)}U/hr")
                return rT
            } else {
                rT.reason.append("; setting current basal of " + round(basal, 2) + " as temp")
                return setTempBasal(basal, 30, profile, rT, currenttemp)
            }
        }

        // eventual BG is at/above target:
        // if iob is over max, just cancel any temps
        rT.reason.append("Eventual BG ${convert_bg(eventualBG)} >= ${convert_bg(max_bg)}, ")
        if (basaliob > max_iob) {
            rT.reason.append("basaliob " + round(basaliob, 2) + " > max_iob " + max_iob)
            if (currenttemp.duration > 15 && round_basal(basal) == round_basal(currenttemp.rate)) {
                rT.reason.append(", temp ${currenttemp.rate} ~ req ${round(basal, 2)}U/hr")
                return rT
            } else {
                rT.reason.append("; setting current basal of " + round(basal, 2) + " as temp")
                return setTempBasal(basal, 30, profile, rT, currenttemp)
            }
        } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

            // insulinReq is the additional insulin required to get down to max bg:
            // if in meal assist mode, check if snoozeBG is lower, as eventualBG is not dependent on IOB
            var insulinReq = round((min(snoozeBG, eventualBG) - target_bg) / sens, 2)
            if (minDelta < 0 && minDelta > expectedDelta) {
                val newinsulinReq = round((insulinReq * (1 - (minDelta / expectedDelta))), 2)
                //console.error("Reducing insulinReq from " + insulinReq + " to " + newinsulinReq);
                insulinReq = newinsulinReq
            }
            // if that would put us over max_iob, then reduce accordingly
            if (insulinReq > max_iob - basaliob) {
                rT.reason.append("max_iob " + max_iob + ", ")
                insulinReq = max_iob - basaliob
            }

            // rate required to deliver insulinReq more insulin over 30m:
            var rate = basal + (2 * insulinReq)
            rate = round_basal(rate)

//        var maxSafeBasal = Math.min(profile.max_basal, 3 * profile.max_daily_basal, 4 * basal);

            val maxSafeBasal = getMaxSafeBasal(profile)

            if (rate > maxSafeBasal) {
                rT.reason.append("adj. req. rate: ${round(rate, 2).withoutZeros()} to maxSafeBasal: ${maxSafeBasal.withoutZeros()}, ")
                rate = round_basal(maxSafeBasal)
            }

            val insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60
            if (insulinScheduled >= insulinReq * 2) { // if current temp would deliver >2x more than the required insulin, lower the rate
                rT.reason.append(
                    "${currenttemp.duration}m@${(currenttemp.rate - basal).toFixed3()} = ${insulinScheduled.toFixed3()} > 2 * req ${insulinReq.withoutZeros()}" + ". Setting temp basal of ${
                        round(rate, 2)
                            .withoutZeros()
                    }U/hr"
                )
                return setTempBasal(rate, 30, profile, rT, currenttemp)
            }

            if (currenttemp.duration == 0) { // no temp is set
                rT.reason.append("no temp, setting ${round(rate, 2).withoutZeros()}U/hr")
                return setTempBasal(rate, 30, profile, rT, currenttemp)
            }

            if (currenttemp.duration > 5 && (round_basal(rate) <= round_basal(currenttemp.rate))) { // if required temp <~ existing temp basal
                rT.reason.append("temp ${(currenttemp.rate).toFixed3()} >~ req ${round(rate, 2).withoutZeros()}U/hr")
                return rT
            }

            // required temp > existing temp basal
            rT.reason.append("temp ${(currenttemp.rate).toFixed3()} < ${round(rate, 2).withoutZeros()}U/hr")
            return setTempBasal(rate, 30, profile, rT, currenttemp)
        }
    }
}
