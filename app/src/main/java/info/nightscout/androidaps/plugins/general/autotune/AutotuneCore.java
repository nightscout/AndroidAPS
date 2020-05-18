package info.nightscout.androidaps.plugins.general.autotune;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.general.autotune.data.BGDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.CRDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose;
import info.nightscout.androidaps.plugins.general.autotune.data.TunedProfile;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AutotuneCore {
    @Inject ActivePluginProvider activePlugin;
    @Inject SP sp;
    @Inject AutotunePlugin autotunePlugin;
    private HasAndroidInjector injector;

    public AutotuneCore (
            HasAndroidInjector injector
    ) {
        this.injector=injector;
        this.injector.androidInjector().inject(this);
    }

    public TunedProfile tuneAllTheThings (PreppedGlucose preppedGlucose, TunedProfile previousAutotune, TunedProfile pumpProfile) {

        //var pumpBasalProfile = pumpProfile.basalprofile;
        double[] pumpBasalProfile = pumpProfile.basal;
        //console.error(pumpBasalProfile);
        double[] basalProfile = previousAutotune.basal;
        //console.error(basalProfile);
        //console.error(isfProfile);
        double isf = previousAutotune.isf;
        //console.error(isf);
        Profile.ProfileValue[] carbRatioProfile = previousAutotune.profile.getIcs();
        double carbRatio = previousAutotune.ic;
        //console.error(carbRatio);
        Double csf = isf / carbRatio;
        Double dia = previousAutotune.dia;
        InsulinInterface insulinInterface = activePlugin.getActiveInsulin();
        int peak=75;
        if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
            peak=55;
        else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK)
            peak=sp.getInt(R.string.key_insulin_oref_peak,75);

        List<CRDatum> crData = preppedGlucose.crData;
        List<BGDatum> csfGlucoseData = preppedGlucose.csfGlucoseData;
        List<BGDatum> isfGlucoseData = preppedGlucose.isfGlucoseData;
        List<BGDatum> basalGlucoseData = preppedGlucose.basalGlucoseData;

        //TODO: Mabe get real pump values like in the original but use profile.isf for now
        Profile pumpISFProfile = null;
        double pumpISF = 0d;
        double pumpCarbRatio = 0d;
        double pumpCSF = 0d;
        // Autosens constraints
        double autotuneMax = SafeParse.stringToDouble(sp.getString("openapsama_autosens_max", "1.2"));
        double autotuneMin = SafeParse.stringToDouble(sp.getString("openapsama_autosens_min", "0.7"));
        double min5minCarbImpact = sp.getDouble("openapsama_min_5m_carbimpact", 3.0);
//*****************************************************************************************************************************************************************************************************

        // Calculate carb ratio (CR) independently of csf and isf
        // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
        // For now, if another meal IOB/COB stacks on top of it, consider them together
        // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
        // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.

        double crTotalCarbs = 0;
        double crTotalInsulin = 0;
        List<CRDatum> crDatum = new ArrayList<CRDatum>();
        for (int i=0; i<crData.size()-1; i++) {
            double crBGChange = crData.get(i).crEndBG - crData.get(i).crInitialBG;
            double crInsulinReq = crBGChange / isf;
            double crIOBChange = crData.get(i).crEndIOB - crData.get(i).crInitialIOB;
            crData.get(i).crInsulinTotal = crData.get(i).crInitialIOB + crData.get(i).crInsulin + crInsulinReq;
            //log.debug(CRDatum.CRInitialIOB, CRDatum.CRInsulin, crInsulinReq, CRInsulinTotal);
            double CR = Round.roundTo(crData.get(i).crCarbs / crData.get(i).crInsulinTotal,0.001);
            //log.debug(crBGChange, crInsulinReq, crIOBChange, CRInsulinTotal);
            //log.debug("CRCarbs:",CRDatum.CRCarbs,"CRInsulin:",CRDatum.CRInsulinTotal,"CR:",CR);
            if (crData.get(i).crInsulin > 0) {
                crTotalCarbs += crData.get(i).crCarbs;
                crTotalInsulin += crData.get(i).crInsulinTotal;
            }
        }

        crTotalInsulin = Round.roundTo(crTotalInsulin,0.001);
        double totalCR = Round.roundTo(crTotalCarbs / crTotalInsulin,0.001);
        log("crTotalCarbs: "+ crTotalCarbs +" crTotalInsulin: "+ crTotalInsulin +" totalCR: "+totalCR);

        // convert the basal profile to hourly if it isn't already
        double hourlyBasalProfile[] = pumpProfile.basal;
        double hourlyPumpProfile[] = pumpProfile.basal;

        //List<Double> basalProfile = new List<Double>;
        /*for (int i=0; i < 24; i++) {
            // autotuned basal profile
            for (int j=0; j < basalProfile.size(); ++j) {
                if (basalProfile[j].minutes <= i * 60) {
                    if (basalProfile[j].rate == 0) {
                        log.debug("ERROR: bad basalProfile",basalProfile[j]);
                        return;
                    }
                    hourlyBasalProfile[i] = JSON.parse(JSON.stringify(basalProfile[j]));
                }
            }
            hourlyBasalProfile[i].i=i;
            hourlyBasalProfile[i].minutes=i*60;
            var zeroPadHour = ("000"+i).slice(-2);
            hourlyBasalProfile[i].start=zeroPadHour + ":00:00";
            hourlyBasalProfile[i].rate=Math.round(hourlyBasalProfile[i].rate*1000)/1000
            // pump basal profile
            if (pumpBasalProfile && pumpBasalProfile[0]) {
                for (int j=0; j < pumpBasalProfile.length; ++j) {
                    //log.debug(pumpBasalProfile[j]);
                    if (pumpBasalProfile[j].rate == 0) {
                        log.debug("ERROR: bad pumpBasalProfile",pumpBasalProfile[j]);
                        return;
                    }
                    if (pumpBasalProfile[j].minutes <= i * 60) {
                        hourlyPumpProfile[i] = JSON.parse(JSON.stringify(pumpBasalProfile[j]));
                    }
                }
                hourlyPumpProfile[i].i=i;
                hourlyPumpProfile[i].minutes=i*60;
                hourlyPumpProfile[i].rate=Math.round(hourlyPumpProfile[i].rate*1000)/1000
            }
        }
        *///log.debug(hourlyPumpProfile);
        //log.debug(hourlyBasalProfile);

        double newHourlyBasalProfile[] = hourlyBasalProfile;


        // look at net deviations for each hour
        for (int hour=0; hour < 24; hour++) {
            double deviations = 0;
            for (int i=0; i < basalGlucoseData.size(); ++i) {
                Date BGTime = null;

                if (basalGlucoseData.get(i).date != 0) {
                    BGTime = new Date(basalGlucoseData.get(i).date);
                }  else {
                    log("Could not determine last BG time");
                }

                int myHour = BGTime.getHours();
                if (hour == myHour) {
                    //log.debug(basalGlucose[i].deviation);
                    deviations += basalGlucoseData.get(i).deviation;
                }
            }
            deviations = Round.roundTo(deviations, 0.001);
            log("Hour "+hour+" total deviations: "+deviations+" mg/dL");
            // calculate how much less or additional basal insulin would have been required to eliminate the deviations
            // only apply 20% of the needed adjustment to keep things relatively stable
            double basalNeeded = 0.2 * deviations / isf;
            basalNeeded = Round.roundTo( basalNeeded,0.01);
            // if basalNeeded is positive, adjust each of the 1-3 hour prior basals by 10% of the needed adjustment
            log("Hour "+hour+" basal adjustment needed: "+basalNeeded+" U/hr");
            if (basalNeeded > 0 ) {
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    //log.debug(offsetHour);
                    newHourlyBasalProfile[offsetHour] = newHourlyBasalProfile[offsetHour] + basalNeeded / 3;
                    newHourlyBasalProfile[offsetHour] = Round.roundTo(newHourlyBasalProfile[offsetHour],0.001);
                }
                // otherwise, figure out the percentage reduction required to the 1-3 hour prior basals
                // and adjust all of them downward proportionally
            } else if (basalNeeded < 0) {
                double threeHourBasal = 0;
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    threeHourBasal += newHourlyBasalProfile[offsetHour];
                }
                double adjustmentRatio = 1.0 + basalNeeded / threeHourBasal;
                //log.debug(adjustmentRatio);
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    newHourlyBasalProfile[offsetHour] = newHourlyBasalProfile[offsetHour] * adjustmentRatio;
                    newHourlyBasalProfile[offsetHour] = Round.roundTo(newHourlyBasalProfile[offsetHour],0.001);
                }
            }
        }
        if (pumpBasalProfile != null ) {
            for (int hour=0; hour < 24; hour++) {
                //log.debug(newHourlyBasalProfile[hour],hourlyPumpProfile[hour].rate*1.2);
                // cap adjustments at autosens_max and autosens_min

                double maxRate = newHourlyBasalProfile[hour] * autotuneMax;
                double minRate = newHourlyBasalProfile[hour] * autotuneMin;
                if (newHourlyBasalProfile[hour] > maxRate ) {
                    log("Limiting hour "+hour+" basal to " + Round.roundTo(maxRate,0.01) + " (which is " + Round.roundTo(autotuneMax,0.01) + " * pump basal of "+hourlyPumpProfile[hour]+")");
                    //log.debug("Limiting hour",hour,"basal to",maxRate.toFixed(2),"(which is 20% above pump basal of",hourlyPumpProfile[hour].rate,")");
                    newHourlyBasalProfile[hour] = maxRate;
                } else if (newHourlyBasalProfile[hour] < minRate ) {
                    log("Limiting hour " + hour + " basal to " + Round.roundTo(minRate,0.01) + " (which is" + autotuneMin + " * pump basal of " + newHourlyBasalProfile[hour] + ")");
                    //log.debug("Limiting hour",hour,"basal to",minRate.toFixed(2),"(which is 20% below pump basal of",hourlyPumpProfile[hour].rate,")");
                    newHourlyBasalProfile[hour] = minRate;
                }
                newHourlyBasalProfile[hour] = Round.roundTo(newHourlyBasalProfile[hour],0.001);
            }
        }

        // some hours of the day rarely have data to tune basals due to meals.
        // when no adjustments are needed to a particular hour, we should adjust it toward the average of the
        // periods before and after it that do have data to be tuned
        int lastAdjustedHour = 0;
        // scan through newHourlyBasalProfile and find hours where the rate is unchanged
        for (int hour=0; hour < 24; hour++) {
            if (hourlyBasalProfile[hour] == newHourlyBasalProfile[hour]) {
                int nextAdjustedHour = 23;
                for (int nextHour = hour; nextHour < 24; nextHour++) {
                    if (! (hourlyBasalProfile[nextHour] == newHourlyBasalProfile[nextHour])) {
                        nextAdjustedHour = nextHour;
                        break;
                    } else {
                        log("At hour: "+nextHour +" " + hourlyBasalProfile[nextHour] + " " +newHourlyBasalProfile[nextHour]);
                    }
                }
                //log.debug(hour, newHourlyBasalProfile);
                // TODO: philoul may be allow % of adjustments in settings with safety limits
                newHourlyBasalProfile[hour] = Round.roundTo( (0.8*hourlyBasalProfile[hour] + 0.1 * newHourlyBasalProfile[lastAdjustedHour] + 0.1 * newHourlyBasalProfile[nextAdjustedHour]),0.001);
//                log.debug("Adjusting hour "+hour+" basal from "+hourlyBasalProfile.get(hour)+" to "+newHourlyBasalProfile.get(hour)+" based on hour "+lastAdjustedHour+" = "+newHourlyBasalProfile.get(lastAdjustedHour)+" and hour "+nextAdjustedHour+"="+newHourlyBasalProfile.get(nextAdjustedHour));
            } else {
                lastAdjustedHour = hour;
            }
        }
        basalProfile = newHourlyBasalProfile;

        // Calculate carb ratio (CR) independently of csf and isf
        // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
        // For now, if another meal IOB/COB stacks on top of it, consider them together
        // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
        // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.


        // calculate net deviations while carbs are absorbing
        // measured from carb entry until COB and deviations both drop to zero

        double deviations = 0;
        double mealCarbs = 0;
        double totalMealCarbs = 0;
        double totalDeviations = 0;
        double fullNewCSF;
        //log.debug(CSFGlucose[0].mealAbsorption);
        //log.debug(CSFGlucose[0]);
        for (int i=0; i < csfGlucoseData.size(); ++i) {
            //log.debug(CSFGlucose[i].mealAbsorption, i);
            if ( csfGlucoseData.get(i).mealAbsorption == "start" ) {
                deviations = 0;
                mealCarbs = csfGlucoseData.get(i).mealCarbs;
            } else if (csfGlucoseData.get(i).mealAbsorption == "end") {
                deviations += csfGlucoseData.get(i).deviation;
                // compare the sum of deviations from start to end vs. current csf * mealCarbs
                //log.debug(csf,mealCarbs);
                double csfRise = csf * mealCarbs;
                //log.debug(deviations,isf);
                //log.debug("csfRise:",csfRise,"deviations:",deviations);
                totalMealCarbs += mealCarbs;
                totalDeviations += deviations;

            } else {
                //todo Philoul check 0 * min5minCarbImpact ???
                deviations += Math.max(0*min5minCarbImpact,csfGlucoseData.get(i).deviation);
                mealCarbs = Math.max(mealCarbs, csfGlucoseData.get(i).mealCarbs);
            }
        }
        // at midnight, write down the mealcarbs as total meal carbs (to prevent special case of when only one meal and it not finishing absorbing by midnight)
        // TODO: figure out what to do with dinner carbs that don't finish absorbing by midnight
        if (totalMealCarbs == 0) { totalMealCarbs += mealCarbs; }
        if (totalDeviations == 0) { totalDeviations += deviations; }
        //log.debug(totalDeviations, totalMealCarbs);
        if (totalMealCarbs == 0) {
            // if no meals today, csf is unchanged
            fullNewCSF = csf;
        } else {
            // how much change would be required to account for all of the deviations
            fullNewCSF = Round.roundTo(totalDeviations/totalMealCarbs,0.01);
        }
        // TODO: philoul may be allow % of adjustments in settings with safety limits
        // only adjust by 20%
        double newCSF = ( 0.8 * csf ) + ( 0.2 * fullNewCSF );
        // safety cap csf
        if (pumpCSF != 0d) {
            double maxCSF = pumpCSF * autotuneMax;
            double minCSF = pumpCSF * autotuneMin;
            if (newCSF > maxCSF) {
                log("Limiting csf to "+ Round.roundTo(maxCSF,0.01) + " (which is " + autotuneMax + "* pump csf of " + pumpCSF + ")");
                newCSF = maxCSF;
            } else if (newCSF < minCSF) {
                log("Limiting csf to " + Round.roundTo(minCSF,0.01) + " (which is" + autotuneMin + "* pump csf of " + pumpCSF + ")");
                newCSF = minCSF;
            } //else { log.debug("newCSF",newCSF,"is close enough to",pumpCSF); }
        }
        double oldCSF = Round.roundTo( csf ,0.001);
        newCSF = Round.roundTo( newCSF ,0.001);
        totalDeviations = Round.roundTo(totalDeviations,0.001);
        log("totalMealCarbs: " + totalMealCarbs + " totalDeviations: " + totalDeviations + " oldCSF " + oldCSF + " fullNewCSF: " + fullNewCSF + " newCSF: " + newCSF);
        // this is where csf is set based on the outputs
        if (newCSF == 0) {
            csf = newCSF;
        }
        double fullNewCR;
        if (totalCR == 0) {
            // if no meals today, CR is unchanged
            fullNewCR = carbRatio;
        } else {
            // how much change would be required to account for all of the deviations
            fullNewCR = totalCR;
        }
        // safety cap fullNewCR
        if (pumpCarbRatio != 0) {
            double maxCR = pumpCarbRatio * autotuneMax;
            double minCR = pumpCarbRatio * autotuneMin;
            if (fullNewCR > maxCR) {
                log("Limiting fullNewCR from " + fullNewCR + " to "+Round.roundTo(maxCR,0.01) + " (which is " + autotuneMax+" * pump CR of " + pumpCarbRatio + ")");
                fullNewCR = maxCR;
            } else if (fullNewCR < minCR) {
                log("Limiting fullNewCR from " + fullNewCR + " to "+Round.roundTo(minCR,0.01) + " (which is " + autotuneMin+" * pump CR of " + pumpCarbRatio + ")");
                fullNewCR = minCR;
            } //else { log.debug("newCR",newCR,"is close enough to",pumpCarbRatio); }
        }
        // TODO: philoul may be allow % of adjustments in settings with safety limits
        // only adjust by 20%
        double newCR = ( 0.8 * carbRatio ) + ( 0.2 * fullNewCR );
        // safety cap newCR
        if (pumpCarbRatio != 0) {
            double maxCR = pumpCarbRatio * autotuneMax;
            double minCR = pumpCarbRatio * autotuneMin;
            if (newCR > maxCR) {
                log("Limiting CR to " + Round.roundTo(maxCR,0.01) + " (which is " + autotuneMax + " * pump CR of " + pumpCarbRatio + ")");
                newCR = maxCR;
            } else if (newCR < minCR) {
                log("Limiting CR to " + Round.roundTo(minCR,0.01) + " (which is " + autotuneMin + " * pump CR of " + pumpCarbRatio + ")");
                newCR = minCR;
            } //else { log.debug("newCR",newCR,"is close enough to",pumpCarbRatio); }
        }
        newCR = Round.roundTo(newCR,0.001);
        log("oldCR: " + carbRatio + " fullNewCR: " + fullNewCR + " newCR: " + newCR);
        // this is where CR is set based on the outputs
        //var ISFFromCRAndCSF = isf;
        if (newCR != 0) {
            carbRatio = newCR;
            //ISFFromCRAndCSF = Math.round( carbRatio * csf * 1000)/1000;
        }



        // calculate median deviation and bgi in data attributable to isf
        List<Double> isfDeviations =  new ArrayList<Double>();
        List<Double> bGIs =   new ArrayList<Double>();
        List<Double> avgDeltas =   new ArrayList<Double>();
        List<Double> ratios =   new ArrayList<Double>();
        int count = 0;
        for (int i=0; i < isfGlucoseData.size(); ++i) {
            double deviation = isfGlucoseData.get(i).deviation;
            isfDeviations.add(deviation);
            double BGI = isfGlucoseData.get(i).BGI;
            bGIs.add(BGI);
            double avgDelta = isfGlucoseData.get(i).AvgDelta;
            avgDeltas.add(avgDelta);
            double ratio = 1 + deviation / BGI;
            //log.debug("Deviation:",deviation,"BGI:",BGI,"avgDelta:",avgDelta,"ratio:",ratio);
            ratios.add(ratio);
            count++;
        }
        Collections.sort(avgDeltas);
        Collections.sort(bGIs);
        Collections.sort(isfDeviations);
        Collections.sort(ratios);
        double p50deviation = IobCobCalculatorPlugin.percentile(isfDeviations.toArray(new Double[isfDeviations.size()]), 0.50);
        double p50BGI =  IobCobCalculatorPlugin.percentile(bGIs.toArray(new Double[bGIs.size()]), 0.50);
        double p50ratios = Round.roundTo( IobCobCalculatorPlugin.percentile(ratios.toArray(new Double[ratios.size()]), 0.50),0.001);
        double fullNewISF = 0d;
        if (count < 10) {
            // leave isf unchanged if fewer than 5 isf data points
            fullNewISF = isf;
        } else {
            // calculate what adjustments to isf would have been necessary to bring median deviation to zero
            fullNewISF = isf * p50ratios;
        }
        fullNewISF = Round.roundTo( fullNewISF,0.001);
        // adjust the target isf to be a weighted average of fullNewISF and pumpISF
        double adjustmentFraction;
/*
        if (typeof(pumpProfile.autotune_isf_adjustmentFraction) !== 'undefined') {
            adjustmentFraction = pumpProfile.autotune_isf_adjustmentFraction;
        } else {*/
        adjustmentFraction = 1.0;
//        }

        // low autosens ratio = high isf
        double maxISF = pumpISF / autotuneMin;
        // high autosens ratio = low isf
        double minISF = pumpISF / autotuneMax;
        double adjustedISF = 0d;
        double newISF = 0d;
        if (pumpISF != 0) {
            if ( fullNewISF < 0 ) {
                adjustedISF = isf;
                log("fullNewISF < 0 setting adjustedISF to "+adjustedISF);
            } else {
                adjustedISF = adjustmentFraction*fullNewISF + (1-adjustmentFraction)*pumpISF;
            }
            // cap adjustedISF before applying 10%
            //log.debug(adjustedISF, maxISF, minISF);
            if (adjustedISF > maxISF) {
                log("Limiting adjusted isf of "+Round.roundTo(adjustedISF,0.01)+" to "+Round.roundTo(maxISF,0.01)+"(which is pump isf of "+pumpISF+"/"+autotuneMin+")");
                adjustedISF = maxISF;
            } else if (adjustedISF < minISF) {
                log("Limiting adjusted isf of"+Round.roundTo(adjustedISF,0.01)+" to "+Round.roundTo(minISF,0.01)+"(which is pump isf of "+pumpISF+"/"+autotuneMax+")");
                adjustedISF = minISF;
            }

            // TODO: philoul may be allow % of adjustments in settings with safety limits Check % (10% in original autotune OAPS for isf ?)
            // and apply 20% of that adjustment
            newISF = ( 0.8 * isf ) + ( 0.2 * adjustedISF );

            if (newISF > maxISF) {
                log("Limiting isf of"+Round.roundTo(newISF,0.01)+"to"+Round.roundTo(maxISF,0.01)+"(which is pump isf of"+pumpISF+"/"+autotuneMin+")");
                newISF = maxISF;
            } else if (newISF < minISF) {
                log("Limiting isf of"+Round.roundTo(newISF,0.01)+"to"+Round.roundTo(minISF,0.01)+"(which is pump isf of"+pumpISF+"/"+autotuneMax+")");
                newISF = minISF;
            }
        }
        newISF = Round.roundTo( newISF,0.001);
        //log.debug(avgRatio);
        //log.debug(newISF);
        p50deviation = Round.roundTo( p50deviation ,0.001);
        p50BGI = Round.roundTo( p50BGI ,0.001);
        adjustedISF = Round.roundTo( adjustedISF,0.001);
        log("p50deviation: "+p50deviation+" p50BGI "+p50BGI+" p50ratios: "+p50ratios+" Old isf: "+isf+" fullNewISF: "+fullNewISF+" adjustedISF: "+adjustedISF+" newISF: "+newISF);

        if (newISF != 0d) {
            isf = newISF;
        }

/*
        // reconstruct updated version of previousAutotune as autotuneOutput
        JSONObject autotuneOutput = new JSONObject();
        try {
            if (previousAutotune != null)
                autotuneOutput = new JSONObject(previousAutotune.toString());
            autotuneOutput.put("basalProfile", basalProfile.toString());
            //isfProfile.sensitivity = isf;
            //autotuneOutput.put("isfProfile", isfProfile);
            autotuneOutput.put("sens", isf);
            autotuneOutput.put("csf", csf);
            //carbRatio = isf / csf;
            carbRatio = Round.roundTo(carbRatio,0.001);
            autotuneOutput.put("carb_ratio", carbRatio);
            previousResult = new JSONObject(autotuneOutput.toString());
        } catch (JSONException e) {}

 */

//****************************************************************************************************************************************************************************************************
        previousAutotune.basal=basalProfile;
        previousAutotune.isf = isf;
        previousAutotune.ic=Round.roundTo(carbRatio,0.001);
        previousAutotune.updateProfile();

        return previousAutotune;
    }

    private void log(String message) {
        autotunePlugin.atLog("[Core] " + message);
    }
}
