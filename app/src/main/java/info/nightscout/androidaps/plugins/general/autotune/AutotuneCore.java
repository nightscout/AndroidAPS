package info.nightscout.androidaps.plugins.general.autotune;

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
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile;
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

    public ATProfile tuneAllTheThings (PreppedGlucose preppedGlucose, ATProfile previousAutotune, ATProfile pumpProfile) {
        //var pumpBasalProfile = pumpProfile.basalprofile;
        double[] pumpBasalProfile = pumpProfile.basal;
        //console.error(pumpBasalProfile);
        double[] basalProfile=previousAutotune.basal;
        //console.error(basalProfile);
        //console.error(isfProfile);
        double isf = previousAutotune.isf;
        //console.error(isf);
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

        List<BGDatum> csfGlucose = preppedGlucose.csfGlucoseData;
        List<BGDatum> isfGlucose = preppedGlucose.isfGlucoseData;
        List<BGDatum> basalGlucose = preppedGlucose.basalGlucoseData;
        List<CRDatum> crData = preppedGlucose.crData;
        //List<DiaDatum> diaDeviations = preppedGlucose.diaDeviations;
        //List<PeakDatum> peakDeviations = preppedGlucose.peakDeviations;

        double pumpISF = pumpProfile.isf;
        double pumpCarbRatio = pumpProfile.ic;
        double pumpCSF = pumpISF / pumpCarbRatio;
        // Autosens constraints
        double autotuneMax = SafeParse.stringToDouble(sp.getString("openapsama_autosens_max", "1.2"));
        double autotuneMin = SafeParse.stringToDouble(sp.getString("openapsama_autosens_min", "0.7"));
        double min5minCarbImpact = sp.getDouble("openapsama_min_5m_carbimpact", 3.0);

/*******Tune DIA and Peak disabled for the first version code below in js********************************************************************************************************
        // tune DIA
        var newDIA = DIA;
        if (diaDeviations) {
            var currentDIAMeanDev = diaDeviations[2].meanDeviation;
            var currentDIARMSDev = diaDeviations[2].RMSDeviation;
            //console.error(DIA,currentDIAMeanDev,currentDIARMSDev);
            var minMeanDeviations = 1000000;
            var minRMSDeviations = 1000000;
            var meanBest = 2;
            var RMSBest = 2;
            for (var i=0; i < diaDeviations.length; i++) {
                var meanDeviations = diaDeviations[i].meanDeviation;
                var RMSDeviations = diaDeviations[i].RMSDeviation;
                if (meanDeviations < minMeanDeviations) {
                    minMeanDeviations = Math.round(meanDeviations*1000)/1000;
                    meanBest = i;
                }
                if (RMSDeviations < minRMSDeviations) {
                    minRMSDeviations = Math.round(RMSDeviations*1000)/1000;
                    RMSBest = i;
                }
            }
            console.error("Best insulinEndTime for meanDeviations:",diaDeviations[meanBest].dia,"hours");
            console.error("Best insulinEndTime for RMSDeviations:",diaDeviations[RMSBest].dia,"hours");
            if ( meanBest < 2 && RMSBest < 2 ) {
                if ( diaDeviations[1].meanDeviation < currentDIAMeanDev * 0.99 && diaDeviations[1].RMSDeviation < currentDIARMSDev * 0.99 ) {
                    newDIA = diaDeviations[1].dia;
                }
            } else if ( meanBest > 2 && RMSBest > 2 ) {
                if ( diaDeviations[3].meanDeviation < currentDIAMeanDev * 0.99 && diaDeviations[3].RMSDeviation < currentDIARMSDev * 0.99 ) {
                    newDIA = diaDeviations[3].dia;
                }
            }
            if ( newDIA > 12 ) {
                console.error("insulinEndTime maximum is 12h: not raising further");
                newDIA=12;
            }
            if ( newDIA !== DIA ) {
                console.error("Adjusting insulinEndTime from",DIA,"to",newDIA,"hours");
            } else {
                console.error("Leaving insulinEndTime unchanged at",DIA,"hours");
            }
        }

        // tune insulinPeakTime
        var newPeak = peak;
        if (peakDeviations && peakDeviations[2]) {
            var currentPeakMeanDev = peakDeviations[2].meanDeviation;
            var currentPeakRMSDev = peakDeviations[2].RMSDeviation;
            //console.error(currentPeakMeanDev);
            minMeanDeviations = 1000000;
            minRMSDeviations = 1000000;
            meanBest = 2;
            RMSBest = 2;
            for (i=0; i < peakDeviations.length; i++) {
                meanDeviations = peakDeviations[i].meanDeviation;
                RMSDeviations = peakDeviations[i].RMSDeviation;
                if (meanDeviations < minMeanDeviations) {
                    minMeanDeviations = Math.round(meanDeviations*1000)/1000;
                    meanBest = i;
                }
                if (RMSDeviations < minRMSDeviations) {
                    minRMSDeviations = Math.round(RMSDeviations*1000)/1000;
                    RMSBest = i;
                }
            }
            console.error("Best insulinPeakTime for meanDeviations:",peakDeviations[meanBest].peak,"minutes");
            console.error("Best insulinPeakTime for RMSDeviations:",peakDeviations[RMSBest].peak,"minutes");
            if ( meanBest < 2 && RMSBest < 2 ) {
                if ( peakDeviations[1].meanDeviation < currentPeakMeanDev * 0.99 && peakDeviations[1].RMSDeviation < currentPeakRMSDev * 0.99 ) {
                    newPeak = peakDeviations[1].peak;
                }
            } else if ( meanBest > 2 && RMSBest > 2 ) {
                if ( peakDeviations[3].meanDeviation < currentPeakMeanDev * 0.99 && peakDeviations[3].RMSDeviation < currentPeakRMSDev * 0.99 ) {
                    newPeak = peakDeviations[3].peak;
                }
            }
            if ( newPeak !== peak ) {
                console.error("Adjusting insulinPeakTime from",peak,"to",newPeak,"minutes");
            } else {
                console.error("Leaving insulinPeakTime unchanged at",peak);
            }
        }

**************************************************************************************************************************************************************/

        // Calculate carb ratio (CR) independently of csf and isf
        // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
        // For now, if another meal IOB/COB stacks on top of it, consider them together
        // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
        // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.

        double crTotalCarbs = 0;
        double crTotalInsulin = 0;
        for (int i=0; i<crData.size()-1; i++) {
            CRDatum crDatum = crData.get(i);
            double crBGChange = crDatum.crEndBG - crDatum.crInitialBG;
            double crInsulinReq = crBGChange / isf;
            double crIOBChange = crDatum.crEndIOB - crDatum.crInitialIOB;
            crDatum.crInsulinTotal = crDatum.crInitialIOB + crDatum.crInsulin + crInsulinReq;
            //log(crDatum.crInitialIOB + " " + crDatum.crInsulin + " " + crInsulinReq + " " + crDatum.crInsulinTotal);
            double cr = Round.roundTo(crDatum.crCarbs / crDatum.crInsulinTotal,0.001);
            //log(crBGChange + " " + crInsulinReq + " " + crIOBChange + " " + crDatum.crInsulinTotal);
            //log("CRCarbs: " + crDatum.crCarbs + " CRInsulin: " + crDatum.crInsulinTotal + " CR:" + cr);
            if (crDatum.crInsulin > 0) {
                crTotalCarbs += crDatum.crCarbs;
                crTotalInsulin += crDatum.crInsulinTotal;
            }
        }

        crTotalInsulin = Round.roundTo(crTotalInsulin,0.001);
        double totalCR = Round.roundTo(crTotalCarbs / crTotalInsulin,0.001);
        log("crTotalCarbs: "+ crTotalCarbs +" crTotalInsulin: "+ crTotalInsulin +" totalCR: "+totalCR);

        // convert the basal profile to hourly if it isn't already
        double hourlyBasalProfile[] = basalProfile;
        double hourlyPumpProfile[] = pumpBasalProfile;

        //log(hourlyPumpProfile.toString());
        //log(hourlyBasalProfile.toString());

        double newHourlyBasalProfile[] = new double[24];
        for (int i = 0 ; i < 24 ; i++)  {
            newHourlyBasalProfile[i]  = hourlyBasalProfile [i];
        }
        int basalUntuned[] = previousAutotune.basalUntuned;

        // look at net deviations for each hour
        for (int hour=0; hour < 24; hour++) {
            double deviations = 0;
            for (int i = 0; i < basalGlucose.size(); ++i) {
                Date BGTime = null;

                if (basalGlucose.get(i).date != 0) {
                    BGTime = new Date(basalGlucose.get(i).date);
                }  else {
                    log("Could not determine last BG time");
                }

                int myHour = BGTime.getHours();
                if (hour == myHour) {
                    //log.debug(basalGlucose[i].deviation);
                    deviations += basalGlucose.get(i).deviation;
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
                    //} else {
                    //    log("At hour: "+nextHour +" " + hourlyBasalProfile[nextHour] + " " +newHourlyBasalProfile[nextHour]);
                    }
                }
                //log.debug(hour, newHourlyBasalProfile);
                newHourlyBasalProfile[hour] = Round.roundTo( (0.8*hourlyBasalProfile[hour] + 0.1 * newHourlyBasalProfile[lastAdjustedHour] + 0.1 * newHourlyBasalProfile[nextAdjustedHour]),0.001);
                basalUntuned[hour]++;
                log("Adjusting hour " + hour + " basal from " + hourlyBasalProfile[hour] + " to "+newHourlyBasalProfile[hour] + " based on hour "+lastAdjustedHour+" = "+newHourlyBasalProfile[lastAdjustedHour] + " and hour " + nextAdjustedHour + " = " + newHourlyBasalProfile[nextAdjustedHour]);
            } else {
                lastAdjustedHour = hour;
            }
        }
        //log(newHourlyBasalProfile.toString());
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
        for (int i = 0; i < csfGlucose.size(); ++i) {
            //log.debug(CSFGlucose[i].mealAbsorption, i);
            if ( csfGlucose.get(i).mealAbsorption == "start" ) {
                deviations = 0;
                mealCarbs = csfGlucose.get(i).mealCarbs;
            } else if (csfGlucose.get(i).mealAbsorption == "end") {
                deviations += csfGlucose.get(i).deviation;
                // compare the sum of deviations from start to end vs. current csf * mealCarbs
                //log.debug(csf,mealCarbs);
                double csfRise = csf * mealCarbs;
                //log.debug(deviations,isf);
                //log.debug("csfRise:",csfRise,"deviations:",deviations);
                totalMealCarbs += mealCarbs;
                totalDeviations += deviations;

            } else {
                //todo Philoul check 0 * min5minCarbImpact ???
                deviations += Math.max(0*min5minCarbImpact, csfGlucose.get(i).deviation);
                mealCarbs = Math.max(mealCarbs, csfGlucose.get(i).mealCarbs);
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
        if (newCSF != 0) {
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
        // don't tune CR out of bounds
        double maxCR = pumpCarbRatio * autotuneMax;
        if (maxCR > 150) { maxCR = 150 ; }
        double minCR = pumpCarbRatio * autotuneMin;
        if (minCR < 3) { minCR = 3 ; }
        // safety cap fullNewCR
        if (pumpCarbRatio != 0) {
            if (fullNewCR > maxCR) {
                log("Limiting fullNewCR from " + fullNewCR + " to "+Round.roundTo(maxCR,0.01) + " (which is " + autotuneMax+" * pump CR of " + pumpCarbRatio + ")");
                fullNewCR = maxCR;
            } else if (fullNewCR < minCR) {
                log("Limiting fullNewCR from " + fullNewCR + " to "+Round.roundTo(minCR,0.01) + " (which is " + autotuneMin+" * pump CR of " + pumpCarbRatio + ")");
                fullNewCR = minCR;
            } //else { log.debug("newCR",newCR,"is close enough to",pumpCarbRatio); }
        }
        // only adjust by 20%
        double newCR = ( 0.8 * carbRatio ) + ( 0.2 * fullNewCR );
        // safety cap newCR
        if (pumpCarbRatio != 0) {
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
        for (int i = 0; i < isfGlucose.size(); ++i) {
            double deviation = isfGlucose.get(i).deviation;
            isfDeviations.add(deviation);
            double BGI = isfGlucose.get(i).BGI;
            bGIs.add(BGI);
            double avgDelta = isfGlucose.get(i).AvgDelta;
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
        double fullNewISF = isf;
        if (count < 10) {
            // leave isf unchanged if fewer than 5 isf data points
            log("Only found " + isfGlucose.size() + " ISF data points, leaving ISF unchanged at " + isf);
        } else {
            // calculate what adjustments to isf would have been necessary to bring median deviation to zero
            fullNewISF = isf * p50ratios;
        }
        fullNewISF = Round.roundTo( fullNewISF,0.001);
        // adjust the target isf to be a weighted average of fullNewISF and pumpISF
        double adjustmentFraction;
/*
        // TODO: philoul may be allow adjustmentFraction in settings with safety limits ?)
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

        previousAutotune.from = preppedGlucose.from;
        previousAutotune.basal=basalProfile;
        previousAutotune.isf = isf;
        previousAutotune.ic=Round.roundTo(carbRatio,0.001);
        previousAutotune.basalUntuned = basalUntuned;
        /* code prepared for future dia/peak integration
        previousAutotune.dia=newDia;
        previousAutotune.insulinPeakTime = newPeak ;
        if (diaDeviations || peakDeviations) {
            autotuneOutput.useCustomPeakTime = true;
        }
        */
        previousAutotune.updateProfile();
        return previousAutotune;
    }

    private void log(String message) {
        autotunePlugin.atLog("[Core] " + message);
    }
}
