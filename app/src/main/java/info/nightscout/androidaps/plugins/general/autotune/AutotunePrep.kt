package info.nightscout.androidaps.plugins.general.autotune

import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.T
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutotunePrep @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val autotuneFS: AutotuneFS,
    private val autotuneIob: AutotuneIob
) {
    fun categorize(tunedprofile: ATProfile): PreppedGlucose? {
        val preppedGlucose = categorizeBGDatums(tunedprofile, tunedprofile.localInsulin)
        val tuneInsulin = sp.getBoolean(R.string.key_autotune_tune_insulin_curve, false)
        if (tuneInsulin) {
            var minDeviations = 1000000.0
            val diaDeviations: MutableList<DiaDeviation> = ArrayList()
            val peakDeviations: MutableList<PeakDeviation> = ArrayList()
            val currentDIA = tunedprofile.localInsulin.dia
            val currentPeak = tunedprofile.localInsulin.peak

            var dia = currentDIA - 2
            val endDIA = currentDIA + 2
            while (dia <= endDIA)
            {
                var sqrtDeviations = 0.0
                var deviations = 0.0
                var deviationsSq = 0.0
                val localInsulin = LocalInsulin("Ins_$currentPeak-$dia", currentPeak, dia)
                val curve_output = categorizeBGDatums(tunedprofile, localInsulin, false)
                val basalGlucose = curve_output?.basalGlucoseData

                basalGlucose?.let {
                    for (hour in 0..23) {
                        for (i in 0..(basalGlucose.size-1)) {
                            val myHour = ((basalGlucose[i].date - MidnightTime.calc(basalGlucose[i].date)) / T.hours(1).msecs()).toInt()
                            if (hour == myHour) {
                                sqrtDeviations += Math.pow(Math.abs(basalGlucose[i].deviation), 0.5)
                                deviations += Math.abs(basalGlucose[i].deviation)
                                deviationsSq += Math.pow(basalGlucose[i].deviation, 2.0)
                            }
                        }
                    }

                    val meanDeviation = Round.roundTo(Math.abs(deviations / basalGlucose.size), 0.001)
                    val smrDeviation = Round.roundTo(Math.pow(sqrtDeviations / basalGlucose.size, 2.0), 0.001)
                    val rmsDeviation = Round.roundTo(Math.pow(deviationsSq / basalGlucose.size, 0.5), 0.001)
                    log("insulinEndTime $dia meanDeviation: $meanDeviation SMRDeviation: $smrDeviation RMSDeviation: $rmsDeviation (mg/dL)")
                    diaDeviations.add(
                        DiaDeviation(
                            dia = dia,
                            meanDeviation = meanDeviation,
                            smrDeviation = smrDeviation,
                            rmsDeviation = rmsDeviation
                        )
                    )
                }
                preppedGlucose?.diaDeviations = diaDeviations

                deviations = Round.roundTo(deviations, 0.001)
                if (deviations < minDeviations)
                    minDeviations = Round.roundTo(deviations, 0.001)
                dia += 1.0
            }

            // consoleError('Optimum insulinEndTime', newDIA, 'mean deviation:', JSMath.Round(minDeviations/basalGlucose.length*1000)/1000, '(mg/dL)');
            //consoleError(diaDeviations);

            minDeviations = 1000000.0
            var peak = currentPeak - 10
            val endPeak = currentPeak + 10
            while (peak <= endPeak)
            {
                var sqrtDeviations = 0.0
                var deviations = 0.0
                var deviationsSq = 0.0
                val localInsulin = LocalInsulin("Ins_$peak-$currentDIA", peak, currentDIA)
                val curve_output = categorizeBGDatums(tunedprofile, localInsulin, false)
                val basalGlucose = curve_output?.basalGlucoseData

                basalGlucose?.let {
                    for (hour in 0..23) {
                        for (i in 0..(basalGlucose.size - 1)) {
                            val myHour = ((basalGlucose[i].date - MidnightTime.calc(basalGlucose[i].date)) / T.hours(1).msecs()).toInt()
                            if (hour == myHour) {
                                //console.error(basalGlucose[i].deviation);
                                sqrtDeviations += Math.pow(Math.abs(basalGlucose[i].deviation), 0.5)
                                deviations += Math.abs(basalGlucose[i].deviation)
                                deviationsSq += Math.pow(basalGlucose[i].deviation, 2.0)
                            }
                        }
                    }

                    val meanDeviation = Round.roundTo(deviations / basalGlucose.size, 0.001)
                    val smrDeviation = Round.roundTo(Math.pow(sqrtDeviations / basalGlucose.size, 2.0), 0.001)
                    val rmsDeviation = Round.roundTo(Math.pow(deviationsSq / basalGlucose.size, 0.5), 0.001)
                    log("insulinPeakTime $peak meanDeviation: $meanDeviation SMRDeviation: $smrDeviation RMSDeviation: $rmsDeviation (mg/dL)")
                    peakDeviations.add(
                        PeakDeviation
                            (
                            peak = peak,
                            meanDeviation = meanDeviation,
                            smrDeviation = smrDeviation,
                            rmsDeviation = rmsDeviation,
                        )
                    )
                }

                deviations = Round.roundTo(deviations, 0.001);
                if (deviations < minDeviations)
                    minDeviations = Round.roundTo(deviations, 0.001)
                peak += 5
            }
            //consoleError($"Optimum insulinPeakTime {newPeak} mean deviation: {JSMath.Round(minDeviations/basalGlucose.Count, 3)} (mg/dL)");
            //consoleError(peakDeviations);
            preppedGlucose?.peakDeviations = peakDeviations
        }

        return preppedGlucose
    }

    //    private static Logger log = LoggerFactory.getLogger(AutotunePlugin.class);
    fun categorizeBGDatums(tunedprofile: ATProfile, localInsulin: LocalInsulin, verbose: Boolean = true): PreppedGlucose? {
        //lib/meals is called before to get only meals data (in AAPS it's done in AutotuneIob)
        val treatments: MutableList<Carbs> = autotuneIob.meals
        val boluses: MutableList<Bolus> = autotuneIob.boluses
        // Bloc between #21 and # 54 replaced by bloc below (just remove BG value below 39, Collections.sort probably not necessary because BG values already sorted...)
        val glucose = autotuneIob.glucose
        val glucoseData: MutableList<GlucoseValue> = ArrayList()
        for (i in glucose.indices) {
            if (glucose[i].value > 39) {
                glucoseData.add(glucose[i])
            }
        }
        if (glucose.size == 0 || glucoseData.size == 0 ) {
            //aapsLogger.debug(LTag.AUTOTUNE, "No BG value received")
            if (verbose)
                log("No BG value received")
            return null
        }

        glucoseData.sortWith(object: Comparator<GlucoseValue>{ override fun compare(o1: GlucoseValue, o2: GlucoseValue): Int = (o2.timestamp - o1.timestamp).toInt() })

        // Bloc below replace bloc between #55 and #71
        // boluses and maxCarbs not used here ?,
        // IOBInputs are for iob calculation (done here in AutotuneIob Class)
        //val boluses = 0
        //val maxCarbs = 0
        if (treatments.size == 0) {
            //aapsLogger.debug(LTag.AUTOTUNE, "No Carbs entries")
            if (verbose)
                log("No Carbs entries")
            //return null
        }
        if (autotuneIob.boluses.size == 0) {
            //aapsLogger.debug(LTag.AUTOTUNE, "No treatment received")
            if (verbose)
                log("No treatment received")
            return null
        }

        var csfGlucoseData: MutableList<BGDatum> = ArrayList()
        var isfGlucoseData: MutableList<BGDatum> = ArrayList()
        var basalGlucoseData: MutableList<BGDatum> = ArrayList()
        val uamGlucoseData: MutableList<BGDatum> = ArrayList()
        val crData: MutableList<CRDatum> = ArrayList()

        //Bloc below replace bloc between #72 and #93
        // I keep it because BG lines in log are consistent between AAPS and Oref0
        val bucketedData: MutableList<BGDatum> = ArrayList()
        bucketedData.add(BGDatum(glucoseData[0], dateUtil))
        //int j=0;
        var k = 0 // index of first value used by bucket
        //for loop to validate and bucket the data
        for (i in 1 until glucoseData.size) {
            val BGTime = glucoseData[i].timestamp
            val lastBGTime = glucoseData[k].timestamp
            val elapsedMinutes = (BGTime - lastBGTime) / (60 * 1000)
            if (Math.abs(elapsedMinutes) >= 2) {
                //j++; // move to next bucket
                k = i // store index of first value used by bucket
                bucketedData.add(BGDatum(glucoseData[i], dateUtil))
            } else {
                // average all readings within time deadband
                val average = glucoseData[k]
                for (l in k + 1 until i + 1) {
                    average.value += glucoseData[l].value
                }
                average.value = average.value / (i - k + 1)
                bucketedData.add(BGDatum(average, dateUtil))
            }
        }

        // Here treatments contains only meals data
        // bloc between #94 and #114 remove meals before first BG value

        // Bloc below replace bloc between #115 and #122 (initialize data before main loop)
        // crInitialxx are declaration to be able to use these data in whole loop
        var calculatingCR = false
        var absorbing = false
        var uam = false // unannounced meal
        var mealCOB = 0.0
        var mealCarbs = 0.0
        var crCarbs = 0.0
        var type = ""
        var crInitialIOB = 0.0
        var crInitialBG = 0.0
        var crInitialCarbTime = 0L

        //categorize.js#123 (Note: don't need fullHistory because data are managed in AutotuneIob Class)
        //Here is main loop between #125 and #366
        // main for loop
        for (i in bucketedData.size - 5 downTo 1) {
            val glucoseDatum = bucketedData[i]
            //log.debug(glucoseDatum);
            val BGTime = glucoseDatum.date

            // As we're processing each data point, go through the treatment.carbs and see if any of them are older than
            // the current BG data point.  If so, add those carbs to COB.
            val treatment = if (treatments.size > 0) treatments[treatments.size - 1] else null
            var myCarbs = 0.0
            if (treatment != null) {
                if (treatment.timestamp < BGTime) {
                    if (treatment.amount > 0.0) {
                        mealCOB += treatment.amount
                        mealCarbs += treatment.amount
                        myCarbs = treatment.amount
                    }
                    treatments.removeAt(treatments.size - 1)
                }
            }
            var bg = 0.0
            var avgDelta = 0.0

            // TODO: re-implement interpolation to avoid issues here with gaps
            // calculate avgDelta as last 4 datapoints to better catch more rises after COB hits zero
            if (bucketedData[i].value != 0.0 && bucketedData[i + 4].value != 0.0) {
                //log.debug(bucketedData[i]);
                bg = bucketedData[i].value
                if (bg < 40 || bucketedData[i + 4].value < 40) {
                    //process.stderr.write("!");
                    continue
                }
                avgDelta = (bg - bucketedData[i + 4].value) / 4
            } else {
                //aapsLogger.debug(LTag.AUTOTUNE, "Could not find glucose data")
                if (verbose)
                    log("Could not find glucose data")
            }
            avgDelta = Round.roundTo(avgDelta, 0.01)
            glucoseDatum.avgDelta = avgDelta

            //sens = ISF
            val sens = tunedprofile.isf

            // for IOB calculations, use the average of the last 4 hours' basals to help convergence;
            // this helps since the basal this hour could be different from previous, especially if with autotune they start to diverge.
            // use the pumpbasalprofile to properly calculate IOB during periods where no temp basal is set
            /* Note Philoul currentPumpBasal never used in oref0 Autotune code
            var currentPumpBasal = pumpprofile.profile.getBasal(BGTime)
            currentPumpBasal += pumpprofile.profile.getBasal(BGTime - 1 * 60 * 60 * 1000)
            currentPumpBasal += pumpprofile.profile.getBasal(BGTime - 2 * 60 * 60 * 1000)
            currentPumpBasal += pumpprofile.profile.getBasal(BGTime - 3 * 60 * 60 * 1000)
            currentPumpBasal = Round.roundTo(currentPumpBasal / 4, 0.001) //CurrentPumpBasal for iob calculation is average of 4 last pumpProfile Basal rate
            */
            // this is the current autotuned basal, used for everything else besides IOB calculations
            val currentBasal = tunedprofile.getBasal(BGTime)

            // basalBGI is BGI of basal insulin activity.
            val basalBGI = Round.roundTo(currentBasal * sens / 60 * 5, 0.01) // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
            //console.log(JSON.stringify(IOBInputs.profile));
            // call iob since calculated elsewhere
            //var iob = getIOB(IOBInputs)[0];
            // in autotune iob is calculated with 6 hours of history data, tunedProfile and average pumpProfile basal rate...
            //log("currentBasal: " + currentBasal + " BGTime: " + BGTime + " / " + dateUtil!!.timeStringWithSeconds(BGTime) + "******************************************************************************************")
            val iob = autotuneIob.getIOB(BGTime, localInsulin)    // add localInsulin to be independent to InsulinPlugin

            // activity times ISF times 5 minutes is BGI
            val BGI = Round.roundTo(-iob.activity * sens * 5, 0.01)
            // datum = one glucose data point (being prepped to store in output)
            glucoseDatum.bgi = BGI
            // calculating deviation
            var deviation = avgDelta - BGI

            // set positive deviations to zero if BG is below 80
            if (bg < 80 && deviation > 0) {
                deviation = 0.0
            }

            // rounding and storing deviation
            deviation = Round.roundTo(deviation, 0.01)
            glucoseDatum.deviation = deviation

            // Then, calculate carb absorption for that 5m interval using the deviation.
            if (mealCOB > 0) {
                val ci = Math.max(deviation, sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, 3.0))
                val absorbed = ci * tunedprofile.ic / sens
                // Store the COB, and use it as the starting point for the next data point.
                mealCOB = Math.max(0.0, mealCOB - absorbed)
            }

            // Calculate carb ratio (CR) independently of CSF and ISF
            // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
            // For now, if another meal IOB/COB stacks on top of it, consider them together
            // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
            // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.
            if (mealCOB > 0 || calculatingCR) {
                // set initial values when we first see COB
                crCarbs += myCarbs
                if (calculatingCR == false) {
                    crInitialIOB = iob.iob
                    crInitialBG = glucoseDatum.value
                    crInitialCarbTime = glucoseDatum.date
                    //aapsLogger.debug(LTag.AUTOTUNE, "CRInitialIOB: $crInitialIOB CRInitialBG: $crInitialBG CRInitialCarbTime: ${dateUtil.toISOString(crInitialCarbTime)}")
                    if (verbose)
                        log("CRInitialIOB: $crInitialIOB CRInitialBG: $crInitialBG CRInitialCarbTime: ${dateUtil.toISOString(crInitialCarbTime)}")
                }
                // keep calculatingCR as long as we have COB or enough IOB
                if (mealCOB > 0 && i > 1) {
                    calculatingCR = true
                } else if (iob.iob > currentBasal / 2 && i > 1) {
                    calculatingCR = true
                    // when COB=0 and IOB drops low enough, record end values and be done calculatingCR
                } else {
                    val crEndIOB = iob.iob
                    val crEndBG = glucoseDatum.value
                    val crEndTime = glucoseDatum.date
                    //aapsLogger.debug(LTag.AUTOTUNE, "CREndIOB: $crEndIOB CREndBG: $crEndBG CREndTime: ${dateUtil.toISOString(crEndTime)}")
                    if (verbose)
                        log("CREndIOB: $crEndIOB CREndBG: $crEndBG CREndTime: ${dateUtil.toISOString(crEndTime)}")
                    val crDatum = CRDatum(dateUtil)
                    crDatum.crInitialBG = crInitialBG
                    crDatum.crInitialIOB = crInitialIOB
                    crDatum.crInitialCarbTime = crInitialCarbTime
                    crDatum.crEndBG = crEndBG
                    crDatum.crEndIOB = crEndIOB
                    crDatum.crEndTime = crEndTime
                    crDatum.crCarbs = crCarbs
                    //log.debug(CRDatum);
                    //String crDataString = "{\"CRInitialIOB\": " + CRInitialIOB + ",   \"CRInitialBG\": " + CRInitialBG + ",   \"CRInitialCarbTime\": " + CRInitialCarbTime + ",   \"CREndIOB\": " + CREndIOB + ",   \"CREndBG\": " + CREndBG + ",   \"CREndTime\": " + CREndTime + ",   \"CRCarbs\": " + CRCarbs + "}";
                    val CRElapsedMinutes = Math.round((crEndTime - crInitialCarbTime) / (1000 * 60).toFloat())

                    //log.debug(CREndTime - CRInitialCarbTime, CRElapsedMinutes);
                    if (CRElapsedMinutes < 60 || i == 1 && mealCOB > 0) {
                        //aapsLogger.debug(LTag.AUTOTUNE, "Ignoring $CRElapsedMinutes m CR period.")
                        if (verbose)
                            log("Ignoring $CRElapsedMinutes m CR period.")
                    } else {
                        crData.add(crDatum)
                    }
                    crCarbs = 0.0
                    calculatingCR = false
                }
            }

            // If mealCOB is zero but all deviations since hitting COB=0 are positive, assign those data points to CSFGlucoseData
            // Once deviations go negative for at least one data point after COB=0, we can use the rest of the data to tune ISF or basals
            if (mealCOB > 0 || absorbing || mealCarbs > 0) {
                // if meal IOB has decayed, then end absorption after this data point unless COB > 0
                absorbing = if (iob.iob < currentBasal / 2) {
                    false
                    // otherwise, as long as deviations are positive, keep tracking carb deviations
                } else if (deviation > 0) {
                    true
                } else {
                    false
                }
                if (!absorbing && mealCOB == 0.0) {
                    mealCarbs = 0.0
                }
                // check previous "type" value, and if it wasn't csf, set a mealAbsorption start flag
                //log.debug(type);
                if (type != "csf") {
                    glucoseDatum.mealAbsorption = "start"
                    //aapsLogger.debug(LTag.AUTOTUNE, "${glucoseDatum.mealAbsorption} carb absorption")
                    if (verbose)
                        log("${glucoseDatum.mealAbsorption} carb absorption")
                }
                type = "csf"
                glucoseDatum.mealCarbs = mealCarbs.toInt()
                //if (i == 0) { glucoseDatum.mealAbsorption = "end"; }
                csfGlucoseData.add(glucoseDatum)
            } else {
                // check previous "type" value, and if it was csf, set a mealAbsorption end flag
                if (type == "csf") {
                    csfGlucoseData[csfGlucoseData.size - 1].mealAbsorption = "end"
                    //aapsLogger.debug(LTag.AUTOTUNE, "${csfGlucoseData[csfGlucoseData.size - 1].mealAbsorption} carb absorption")
                    if (verbose)
                        log("${csfGlucoseData[csfGlucoseData.size - 1].mealAbsorption} carb absorption")
                }
                if (iob.iob > 2 * currentBasal || deviation > 6 || uam) {
                    uam = if (deviation > 0) {
                        true
                    } else {
                        false
                    }
                    if (type != "uam") {
                        glucoseDatum.uamAbsorption = "start"
                        //aapsLogger.debug(LTag.AUTOTUNE, "${glucoseDatum.uamAbsorption} unannnounced meal absorption")
                        if (verbose)
                            log(glucoseDatum.uamAbsorption + " unannnounced meal absorption")
                    }
                    type = "uam"
                    uamGlucoseData.add(glucoseDatum)
                } else {
                    if (type == "uam") {
                        //aapsLogger.debug(LTag.AUTOTUNE, "end unannounced meal absorption")
                        if (verbose)
                            log("end unannounced meal absorption")
                    }

                    // Go through the remaining time periods and divide them into periods where scheduled basal insulin activity dominates. This would be determined by calculating the BG impact of scheduled basal insulin
                    // (for example 1U/hr * 48 mg/dL/U ISF = 48 mg/dL/hr = 5 mg/dL/5m), and comparing that to BGI from bolus and net basal insulin activity.
                    // When BGI is positive (insulin activity is negative), we want to use that data to tune basals
                    // When BGI is smaller than about 1/4 of basalBGI, we want to use that data to tune basals
                    // When BGI is negative and more than about 1/4 of basalBGI, we can use that data to tune ISF,
                    // unless avgDelta is positive: then that's some sort of unexplained rise we don't want to use for ISF, so that means basals
                    if (basalBGI > -4 * BGI) {
                        type = "basal"
                        basalGlucoseData.add(glucoseDatum)
                    } else {
                        if (avgDelta > 0 && avgDelta > -2 * BGI) {
                            //type="unknown"
                            type = "basal"
                            basalGlucoseData.add(glucoseDatum)
                        } else {
                            type = "ISF"
                            isfGlucoseData.add(glucoseDatum)
                        }
                    }
                }
            }
            // debug line to print out all the things
            //aapsLogger.debug(LTag.AUTOTUNE, "${(if (absorbing) 1 else 0)} mealCOB: ${Round.roundTo(mealCOB, 0.1)} mealCarbs: ${Math.round(mealCarbs)} basalBGI: ${Round.roundTo(basalBGI, 0.1)} BGI: ${Round.roundTo(BGI, 0.1)} IOB: ${iob.iob} Activity: ${iob.activity} at ${dateUtil.timeStringWithSeconds(BGTime)} dev: $deviation avgDelta: $avgDelta $type")
            if (verbose)
                log("${(if (absorbing) 1 else 0)} mealCOB: ${Round.roundTo(mealCOB, 0.1)} mealCarbs: ${Math.round(mealCarbs)} basalBGI: ${Round.roundTo(basalBGI, 0.1)} BGI: ${Round
                        .roundTo(BGI, 0.1)} IOB: ${iob.iob} Activity: ${iob.activity} at ${dateUtil.timeStringWithSeconds(BGTime)} dev: $deviation avgDelta: $avgDelta $type")
        }

//****************************************************************************************************************************************

// categorize.js Lines 372-383
        for (crDatum in crData) {
            crDatum.crInsulin = dosed(crDatum.crInitialCarbTime, crDatum.crEndTime, boluses)
        }
        // categorize.js Lines 384-436
        val CSFLength = csfGlucoseData.size
        var ISFLength = isfGlucoseData.size
        val UAMLength = uamGlucoseData.size
        var basalLength = basalGlucoseData.size
        if (sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false)) {
            //aapsLogger.debug(LTag.AUTOTUNE, "Categorizing all UAM data as basal.")
            if (verbose)
                log("Categorizing all UAM data as basal.")
            basalGlucoseData.addAll(uamGlucoseData)
        } else if (CSFLength > 12) {
            //aapsLogger.debug(LTag.AUTOTUNE, "Found at least 1h of carb: assuming meals were announced, and categorizing UAM data as basal.")
            if (verbose)
                log("Found at least 1h of carb: assuming meals were announced, and categorizing UAM data as basal.")
            basalGlucoseData.addAll(uamGlucoseData)
        } else {
            if (2 * basalLength < UAMLength) {
                //log.debug(basalGlucoseData, UAMGlucoseData);
                //aapsLogger.debug(LTag.AUTOTUNE, "Warning: too many deviations categorized as UnAnnounced Meals")
                //aapsLogger.debug(LTag.AUTOTUNE, "Adding $UAMLength UAM deviations to $basalLength basal ones")
                if (verbose) {
                    log("Warning: too many deviations categorized as UnAnnounced Meals")
                    log("Adding $UAMLength UAM deviations to $basalLength basal ones")
                }
                basalGlucoseData.addAll(uamGlucoseData)
                //log.debug(basalGlucoseData);
                // if too much data is excluded as UAM, add in the UAM deviations, but then discard the highest 50%
                basalGlucoseData.sortWith(object: Comparator<BGDatum>{ override fun compare(o1: BGDatum, o2: BGDatum): Int = (100 * o1.deviation - 100 * o2.deviation).toInt() })  //deviation rouded to 0.01, so *100 to avoid crash during sort
                val newBasalGlucose: MutableList<BGDatum> = ArrayList()
                for (i in 0 until basalGlucoseData.size / 2) {
                    newBasalGlucose.add(basalGlucoseData[i])
                }
                //log.debug(newBasalGlucose);
                basalGlucoseData = newBasalGlucose
                //aapsLogger.debug(LTag.AUTOTUNE, "and selecting the lowest 50%, leaving ${basalGlucoseData.size} basal+UAM ones")
                if (verbose)
                    log("and selecting the lowest 50%, leaving ${basalGlucoseData.size} basal+UAM ones")
            }
            if (2 * ISFLength < UAMLength) {
                //aapsLogger.debug(LTag.AUTOTUNE, "Adding $UAMLength UAM deviations to $ISFLength ISF ones")
                if (verbose)
                    log("Adding $UAMLength UAM deviations to $ISFLength ISF ones")
                isfGlucoseData.addAll(uamGlucoseData)
                // if too much data is excluded as UAM, add in the UAM deviations to ISF, but then discard the highest 50%
                isfGlucoseData.sortWith(object: Comparator<BGDatum>{ override fun compare(o1: BGDatum, o2: BGDatum): Int = (100 * o1.deviation - 100 * o2.deviation).toInt() })   //deviation rouded to 0.01, so *100 to avoid crash during sort
                val newISFGlucose: MutableList<BGDatum> = ArrayList()
                for (i in 0 until isfGlucoseData.size / 2) {
                    newISFGlucose.add(isfGlucoseData[i])
                }
                //console.error(newISFGlucose);
                isfGlucoseData = newISFGlucose
                //aapsLogger.debug(LTag.AUTOTUNE, "and selecting the lowest 50%, leaving ${isfGlucoseData.size} ISF+UAM ones")
                if (verbose)
                    log("and selecting the lowest 50%, leaving ${isfGlucoseData.size} ISF+UAM ones")
                //log.error(ISFGlucoseData.length, UAMLength);
            }
        }
        basalLength = basalGlucoseData.size
        ISFLength = isfGlucoseData.size
        if (4 * basalLength + ISFLength < CSFLength && ISFLength < 10) {
            //aapsLogger.debug(LTag.AUTOTUNE, "Warning: too many deviations categorized as meals")
            //aapsLogger.debug(LTag.AUTOTUNE, "Adding $CSFLength CSF deviations to $ISFLength ISF ones")
            if (verbose) {
                log("Warning: too many deviations categorized as meals")
                //log.debug("Adding",CSFLength,"CSF deviations to",basalLength,"basal ones");
                //var basalGlucoseData = basalGlucoseData.concat(CSFGlucoseData);
                log("Adding $CSFLength CSF deviations to $ISFLength ISF ones")
            }
            isfGlucoseData.addAll(csfGlucoseData)
            csfGlucoseData = ArrayList()
        }

// categorize.js Lines 437-444
        //aapsLogger.debug(LTag.AUTOTUNE, "CRData: ${crData.size} CSFGlucoseData: ${csfGlucoseData.size} ISFGlucoseData: ${isfGlucoseData.size} BasalGlucoseData: ${basalGlucoseData.size}")
        if (verbose)
            log("CRData: ${crData.size} CSFGlucoseData: ${csfGlucoseData.size} ISFGlucoseData: ${isfGlucoseData.size} BasalGlucoseData: ${basalGlucoseData.size}")

        return PreppedGlucose(autotuneIob.startBG, crData, csfGlucoseData, isfGlucoseData, basalGlucoseData, dateUtil)
    }

    //dosed.js full
    private fun dosed(start: Long, end: Long, treatments: List<Bolus>): Double {
        var insulinDosed = 0.0
        //aapsLogger.debug(LTag.AUTOTUNE, "No treatments to process.")
        if (treatments.size == 0) {
            log("No treatments to process.")
            return 0.0
        }
        for (treatment in treatments) {
            if (treatment.amount != 0.0 && treatment.timestamp > start && treatment.timestamp <= end) {
                insulinDosed += treatment.amount
                //log("CRDATA;${dateUtil.toISOString(start)};${dateUtil.toISOString(end)};${treatment.timestamp};${treatment.amount};$insulinDosed")
            }
        }
        //log("insulin dosed: " + insulinDosed);
        return Round.roundTo(insulinDosed, 0.001)
    }

    private fun log(message: String) {
        autotuneFS.atLog("[Prep] $message")
    }
}