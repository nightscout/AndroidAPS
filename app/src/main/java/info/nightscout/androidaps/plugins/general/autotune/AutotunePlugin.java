package info.nightscout.androidaps.plugins.general.autotune;

import android.content.Context;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.general.autotune.data.BGDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.CRDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.general.autotune.data.*;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by Rumen Georgiev on 1/29/2018.
 idea is to port Autotune from OpenAPS to java
 let's start by taking 1 day of data from NS, and comparing it to ideal BG
 TODO: Make this plugin disabable
 TODO: ADD Support for multiple ISF, IC and target values to the exported profile
 START_DAYS_AGO=1  # Default to yesterday if not otherwise specified
 END_DAYS_AGO=1  # Default to yesterday if not otherwise specified
 EXPORT_EXCEL="" # Default is to not export to Microsoft Excel
 TERMINAL_LOGGING=true
 CATEGORIZE_UAM_AS_BASAL=false
 RECOMMENDS_REPORT=true
 UNKNOWN_OPTION=""
 FIRST WE NEED THE DATA PREPARATION
 -- oref0 autotuning data prep tool
 -- Collects and divides up glucose data for periods dominated by carb absorption,
 -- correction insulin, or basal insulin, and adds in avgDelta and deviations,
 -- for use in oref0 autotuning algorithm
 -- get glucoseData and sort it
 -- get profile - done
 -- get treatments

 * Update by philoul on 03/02/2020
 *  property getIsf / getTargetLow / getTargetHigh replaced by getIsfMgdl / getTargetLowMgdl / getTargetHighMgdl in Profile objects
 *  TODO: add Preference for main settings (categorize_uam_as_basal, nb of days, may be advanced settings for % of adjustment (default 20%))
 */

@Singleton
public class AutotunePlugin extends PluginBase {
    private static AutotunePlugin tuneProfile = null;
    private static Logger log = LoggerFactory.getLogger(AutotunePlugin.class);
    private static Profile profile;
    private static Profile pumpProfile;
    private static List<Double> basalsResult = new ArrayList<Double>();
    private static List<Treatment> treatments;
    private String logString="";
    private List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> UAMGlucoseData = new ArrayList<BGDatum>();
    private List<CRDatum> CRData = new ArrayList<CRDatum>();
    private JSONObject previousResult = null;
    private Profile autotuneResult;
    private String tunedProfileName = "Autotune";
    //copied from IobCobCalculator
    private static final int autotuneStartHour = 4;
    public static String result ="Press Run";
    public static Date lastRun=null;
    public boolean nsDataDownloaded = false;
    private PreppedGlucose preppedGlucose =null;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final Context context;
    private final RxBusWrapper rxBus;
    private final ActivePluginProvider activePlugin;
    private final TreatmentsPlugin treatmentsPlugin;
    private final IobCobCalculatorPlugin iobCobCalculatorPlugin;
    private AutotunePrep autotunePrep;
    private AutotuneCore autotuneCore;
    private AutotuneIob autotuneIob;
    private AAPSLogger aapsLogger;

    private final SP sp;
    public final HasAndroidInjector injector;

    //Todo add Inject if possible AutotunePrep and AutotuneCore... I don't know how to do that easily :-(
    @Inject
    public AutotunePlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            ProfileFunction profileFunction,
            Context context,
            SP sp,
            ActivePluginProvider activePlugin,
            TreatmentsPlugin treatmentsPlugin,
            IobCobCalculatorPlugin iobCobCalculatorPlugin
    )  {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(AutotuneFragment.class.getName())
                .pluginName(R.string.autotune)
                .shortName(R.string.autotune_shortname)
                .description(R.string.autotune_description)
                .preferencesId(R.xml.pref_autotune),
                aapsLogger, resourceHelper, injector
        );
        //create autotune subfolder for autotune files if not exists
        AutotuneFS.createAutotuneFolder();
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        this.rxBus = rxBus;
        this.context = context;
        this.activePlugin = activePlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.iobCobCalculatorPlugin = iobCobCalculatorPlugin;
        this.injector = injector;
        this.sp=sp;
        this.aapsLogger = aapsLogger;
    }

//    @Override
    public String getFragmentClass() {
        return AutotuneFragment.class.getName();
    }

    public void invoke(String initiator, boolean allowNotification) {
        // invoke
    }


    public void getProfile(){
        //get active profile
        profile = profileFunction.getProfile();
        if(profile==null)
            log.debug("Autotune no profile selected");
    }

    public synchronized Double getBasal(Integer hour){
        getProfile();
        if(profile.equals(null))
            return 0d;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }

    public String tuneAllTheThings() throws JSONException {

//                var previousAutotune = inputs.previousAutotune;
        //log.debug(previousAutotune);
        JSONObject previousAutotune = new JSONObject();
        if(previousResult != null) {
            previousAutotune = previousResult.optJSONObject("basalProfile");
            log.debug("PrevResult is: " + previousResult.toString());
        }
        List<Double> basalProfile  = new ArrayList<Double>();
        //Parsing last result
        if(previousAutotune != null) {
            for (int i = 0; i < 24; i++) {
                basalProfile.add(previousAutotune.optDouble("" + i));
            }
        }

        Profile pumpProfile = profile;
        Profile pumpBasalProfile = profile;
        //TODO: Mabe get real pump values like in the original but use profile.ISF for now
        Profile pumpISFProfile = null;
        double pumpISF = 0d;
        double pumpCarbRatio = 0d;
        double pumpCSF = 0d;
        // Autosens constraints
        double autotuneMax = SafeParse.stringToDouble(sp.getString("openapsama_autosens_max", "1.2"));
        double autotuneMin = SafeParse.stringToDouble(sp.getString("openapsama_autosens_min", "0.7"));
        double min5minCarbImpact = sp.getDouble("openapsama_min_5m_carbimpact", 3.0);
        //log.debug(pumpBasalProfile);
//        var basalProfile = previousAutotune.basalprofile;
        //log.debug(basalProfile);
//        var isfProfile = previousAutotune.isfProfile;
        //log.debug(isfProfile);
// TODO: Philoul retrieve the units from the main parameters instead of the profile or delete 5 lines below if not used
        int toMgDl = 1;
        if(profile.equals(null))
            return null;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        double ISF = profile.getIsfMgdl();

        //log.debug(ISF);
        double carbRatio = profile.getIc();
        //log.debug(carbRatio);
        double CSF = ISF / carbRatio;

        //Compy profile values to pump ones
        pumpISF = ISF;
        log.debug("PumpISF is: "+pumpISF);
        pumpCarbRatio = carbRatio;
        pumpCSF = CSF;
        // conditional on there being a pump profile; if not then skip
        if (pumpProfile != null) { pumpISFProfile = pumpProfile; }
        if (pumpISFProfile != null && pumpISFProfile.getIsfMgdl() != 0d) {
            pumpISF = pumpISFProfile.getIsfMgdl();
            pumpCarbRatio = pumpProfile.getIc();
            pumpCSF = pumpISF / pumpCarbRatio;
            log.debug("After getting pumpProfile: pumpISF is "+pumpISF+" pumpCSF "+pumpCSF+" pumpCarbRatio "+pumpCarbRatio);
        }
        if (carbRatio == 0d) { carbRatio = pumpCarbRatio; }
        if (CSF == 0d) { CSF = pumpCSF; }
        if (ISF == 0d) { ISF = pumpISF; }
        //log.debug(CSF);
//        List<BGDatum> preppedGlucose = preppedGlucose;
        List<BGDatum> CSFGlucose = preppedGlucose.csfGlucoseData;
        //log.debug(CSFGlucose[0]);
        List<BGDatum> ISFGlucose = preppedGlucose.isfGlucoseData;
        //log.debug(ISFGlucose[0]);
        List<BGDatum> basalGlucose = preppedGlucose.basalGlucoseData;
        //log.debug(basalGlucose[0]);
        List<CRDatum> CRData = preppedGlucose.crData;
        //log.debug(CRData);

        // Calculate carb ratio (CR) independently of CSF and ISF
        // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
        // For now, if another meal IOB/COB stacks on top of it, consider them together
        // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
        // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.

        double CRTotalCarbs = 0;
        double CRTotalInsulin = 0;
        List<CRDatum> CRDatum = new ArrayList<CRDatum>();
        for (int i=0; i<CRData.size()-1; i++) {
                double CRBGChange = CRData.get(i).crEndBG - CRData.get(i).crInitialBG;
                double CRInsulinReq = CRBGChange / ISF;
                double CRIOBChange = CRData.get(i).crEndIOB - CRData.get(i).crInitialIOB;
                CRData.get(i).crInsulinTotal = CRData.get(i).crInitialIOB + CRData.get(i).crInsulin + CRInsulinReq;
                //log.debug(CRDatum.CRInitialIOB, CRDatum.CRInsulin, CRInsulinReq, CRInsulinTotal);
                double CR = Math.round(CRData.get(i).crCarbs / CRData.get(i).crInsulinTotal * 1000) / 1000;
                //log.debug(CRBGChange, CRInsulinReq, CRIOBChange, CRInsulinTotal);
                //log.debug("CRCarbs:",CRDatum.CRCarbs,"CRInsulin:",CRDatum.CRInsulinTotal,"CR:",CR);
                if (CRData.get(i).crInsulin > 0) {
                    CRTotalCarbs += CRData.get(i).crCarbs;
                    CRTotalInsulin += CRData.get(i).crInsulinTotal;
                }
        }

        CRTotalInsulin = Math.round(CRTotalInsulin*1000)/1000;
        double totalCR = Math.round( CRTotalCarbs / CRTotalInsulin * 1000 )/1000;
        log.debug("CRTotalCarbs: "+CRTotalCarbs+" CRTotalInsulin: "+CRTotalInsulin+" totalCR: "+totalCR);

        // convert the basal profile to hourly if it isn't already
        List<Double> hourlyBasalProfile = new ArrayList<Double>();
        List<Double> hourlyPumpProfile = new ArrayList<Double>();
        for(int i=0; i<24; i++) {
            hourlyBasalProfile.add(i, getBasal(i));
//            log.debug("StartBasal at hour "+i+" is "+hourlyBasalProfile.get(i));
            hourlyPumpProfile.add(i, getBasal(i));
        }
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
        List<Double> newHourlyBasalProfile = new ArrayList<Double>();
        for(int i=0; i<hourlyBasalProfile.size();i++){
            newHourlyBasalProfile.add(hourlyBasalProfile.get(i));
        }

        // look at net deviations for each hour
        for (int hour=0; hour < 24; hour++) {
            double deviations = 0;
            for (int i=0; i < basalGlucose.size(); ++i) {
                Date BGTime = null;

                if (basalGlucose.get(i).date != 0) {
                    BGTime = new Date(basalGlucose.get(i).date);
                }  else {
                    log.debug("Could not determine last BG time");
                }

                int myHour = BGTime.getHours();
                if (hour == myHour) {
                    //log.debug(basalGlucose[i].deviation);
                    deviations += basalGlucose.get(i).deviation;
                }
            }
            deviations = round( deviations,3);
            log.debug("Hour "+hour+" total deviations: "+deviations+" mg/dL");
            // calculate how much less or additional basal insulin would have been required to eliminate the deviations
            // only apply 20% of the needed adjustment to keep things relatively stable
            double basalNeeded = 0.2 * deviations / ISF;
            basalNeeded = round( basalNeeded,2);
            // if basalNeeded is positive, adjust each of the 1-3 hour prior basals by 10% of the needed adjustment
            log.debug("Hour "+hour+" basal adjustment needed: "+basalNeeded+" U/hr");
            if (basalNeeded > 0 ) {
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    //log.debug(offsetHour);
                    newHourlyBasalProfile.set(offsetHour, newHourlyBasalProfile.get(offsetHour) + basalNeeded / 3);
                    newHourlyBasalProfile.set(offsetHour, round(newHourlyBasalProfile.get(offsetHour),3));
                }
                // otherwise, figure out the percentage reduction required to the 1-3 hour prior basals
                // and adjust all of them downward proportionally
            } else if (basalNeeded < 0) {
                double threeHourBasal = 0;
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    threeHourBasal += newHourlyBasalProfile.get(offsetHour);
                }
                double adjustmentRatio = 1.0 + basalNeeded / threeHourBasal;
                //log.debug(adjustmentRatio);
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    newHourlyBasalProfile.set(offsetHour, newHourlyBasalProfile.get(offsetHour) * adjustmentRatio);
                    newHourlyBasalProfile.set(offsetHour, round(newHourlyBasalProfile.get(offsetHour),3));
                }
            }
        }
        if (pumpBasalProfile != null && pumpBasalProfile.getBasalValues() != null) {
            for (int hour=0; hour < 24; hour++) {
                //log.debug(newHourlyBasalProfile[hour],hourlyPumpProfile[hour].rate*1.2);
                // cap adjustments at autosens_max and autosens_min

                double maxRate = newHourlyBasalProfile.get(hour) * autotuneMax;
                double minRate = newHourlyBasalProfile.get(hour) * autotuneMin;
                if (newHourlyBasalProfile.get(hour) > maxRate ) {
                    log.debug("Limiting hour"+hour+"basal to"+round(maxRate,2)+"(which is "+round(autotuneMax,2)+"* pump basal of"+hourlyPumpProfile.get(hour)+")");
                    //log.debug("Limiting hour",hour,"basal to",maxRate.toFixed(2),"(which is 20% above pump basal of",hourlyPumpProfile[hour].rate,")");
                    newHourlyBasalProfile.set(hour,maxRate);
                } else if (newHourlyBasalProfile.get(hour) < minRate ) {
                    log.debug("Limiting hour",hour,"basal to"+round(minRate,2)+"(which is"+autotuneMin+"* pump basal of"+newHourlyBasalProfile.get(hour)+")");
                    //log.debug("Limiting hour",hour,"basal to",minRate.toFixed(2),"(which is 20% below pump basal of",hourlyPumpProfile[hour].rate,")");
                    newHourlyBasalProfile.set(hour, minRate);
                }
                newHourlyBasalProfile.set(hour, round(newHourlyBasalProfile.get(hour),3));
            }
        }

        // some hours of the day rarely have data to tune basals due to meals.
        // when no adjustments are needed to a particular hour, we should adjust it toward the average of the
        // periods before and after it that do have data to be tuned
        int lastAdjustedHour = 0;
        // scan through newHourlyBasalProfile and find hours where the rate is unchanged
        for (int hour=0; hour < 24; hour++) {
            if (hourlyBasalProfile.get(hour).equals(newHourlyBasalProfile.get(hour))) {
                int nextAdjustedHour = 23;
                for (int nextHour = hour; nextHour < 24; nextHour++) {
                    if (! (hourlyBasalProfile.get(nextHour) == newHourlyBasalProfile.get(nextHour))) {
                        nextAdjustedHour = nextHour;
                        break;
                        } else {
                        log.debug("At hour: "+nextHour +" " +hourlyBasalProfile.get(nextHour)+" " +newHourlyBasalProfile.get(nextHour));
                    }
                }
                //log.debug(hour, newHourlyBasalProfile);
                newHourlyBasalProfile.set(hour, round( (0.8*hourlyBasalProfile.get(hour) + 0.1*newHourlyBasalProfile.get(lastAdjustedHour) + 0.1*newHourlyBasalProfile.get(nextAdjustedHour)),3));
//                log.debug("Adjusting hour "+hour+" basal from "+hourlyBasalProfile.get(hour)+" to "+newHourlyBasalProfile.get(hour)+" based on hour "+lastAdjustedHour+" = "+newHourlyBasalProfile.get(lastAdjustedHour)+" and hour "+nextAdjustedHour+"="+newHourlyBasalProfile.get(nextAdjustedHour));
            } else {
                lastAdjustedHour = hour;
            }
        }
        basalProfile = newHourlyBasalProfile;

        // Calculate carb ratio (CR) independently of CSF and ISF
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
        for (int i=0; i < CSFGlucose.size(); ++i) {
            //log.debug(CSFGlucose[i].mealAbsorption, i);
            if ( CSFGlucose.get(i).mealAbsorption == "start" ) {
                deviations = 0;
                mealCarbs = CSFGlucose.get(i).mealCarbs;
            } else if (CSFGlucose.get(i).mealAbsorption == "end") {
                deviations += CSFGlucose.get(i).deviation;
                // compare the sum of deviations from start to end vs. current CSF * mealCarbs
                //log.debug(CSF,mealCarbs);
                double csfRise = CSF * mealCarbs;
                //log.debug(deviations,ISF);
                //log.debug("csfRise:",csfRise,"deviations:",deviations);
                totalMealCarbs += mealCarbs;
                totalDeviations += deviations;

            } else {
                deviations += Math.max(0*min5minCarbImpact,CSFGlucose.get(i).deviation);
                mealCarbs = Math.max(mealCarbs, CSFGlucose.get(i).mealCarbs);
            }
        }
        // at midnight, write down the mealcarbs as total meal carbs (to prevent special case of when only one meal and it not finishing absorbing by midnight)
        // TODO: figure out what to do with dinner carbs that don't finish absorbing by midnight
        if (totalMealCarbs == 0) { totalMealCarbs += mealCarbs; }
        if (totalDeviations == 0) { totalDeviations += deviations; }
        //log.debug(totalDeviations, totalMealCarbs);
        if (totalMealCarbs == 0) {
            // if no meals today, CSF is unchanged
            fullNewCSF = CSF;
        } else {
            // how much change would be required to account for all of the deviations
            fullNewCSF = Math.round( (totalDeviations / totalMealCarbs)*100 )/100;
        }
        // TODO: philoul may be allow % of adjustments in settings with safety limits
        // only adjust by 20%
        double newCSF = ( 0.8 * CSF ) + ( 0.2 * fullNewCSF );
        // safety cap CSF
        if (pumpCSF != 0d) {
            double maxCSF = pumpCSF * autotuneMax;
            double minCSF = pumpCSF * autotuneMin;
            if (newCSF > maxCSF) {
                log.debug("Limiting CSF to "+round(maxCSF,2)+"(which is "+autotuneMax+"* pump CSF of "+pumpCSF+")");
                newCSF = maxCSF;
            } else if (newCSF < minCSF) {
                log.debug("Limiting CSF to"+round(minCSF,2)+"(which is"+autotuneMin+"* pump CSF of"+pumpCSF+")");
                newCSF = minCSF;
            } //else { log.debug("newCSF",newCSF,"is close enough to",pumpCSF); }
        }
        double oldCSF = Math.round( CSF * 1000 ) / 1000;
        newCSF = Math.round( newCSF * 1000 ) / 1000;
        totalDeviations = Math.round ( totalDeviations * 1000 )/1000;
        log.debug("totalMealCarbs: "+totalMealCarbs+" totalDeviations: "+totalDeviations+" oldCSF "+oldCSF+" fullNewCSF: "+fullNewCSF+" newCSF: "+newCSF);
        // this is where CSF is set based on the outputs
        if (newCSF == 0) {
            CSF = newCSF;
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
                log.debug("Limiting fullNewCR from"+fullNewCR+"to"+round(maxCR,2)+"(which is"+autotuneMax+"* pump CR of"+pumpCarbRatio+")");
                fullNewCR = maxCR;
            } else if (fullNewCR < minCR) {
                log.debug("Limiting fullNewCR from"+fullNewCR+"to"+round(minCR,2)+"(which is"+autotuneMin+"* pump CR of"+pumpCarbRatio+")");
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
                log.debug("Limiting CR to "+round(maxCR,2)+"(which is"+autotuneMax+"* pump CR of"+pumpCarbRatio+")");
                newCR = maxCR;
            } else if (newCR < minCR) {
                log.debug("Limiting CR to "+round(minCR,2)+"(which is"+autotuneMin+"* pump CR of"+pumpCarbRatio+")");
                newCR = minCR;
            } //else { log.debug("newCR",newCR,"is close enough to",pumpCarbRatio); }
        }
        newCR = Math.round( newCR * 1000 ) / 1000;
        log.debug("oldCR: "+carbRatio+" fullNewCR: "+fullNewCR+" newCR: "+newCR);
        // this is where CR is set based on the outputs
        //var ISFFromCRAndCSF = ISF;
        if (newCR != 0) {
            carbRatio = newCR;
            //ISFFromCRAndCSF = Math.round( carbRatio * CSF * 1000)/1000;
        }



        // calculate median deviation and bgi in data attributable to ISF
        List<Double> ISFdeviations =  new ArrayList<Double>();
        List<Double> BGIs =   new ArrayList<Double>();
        List<Double> avgDeltas =   new ArrayList<Double>();
        List<Double> ratios =   new ArrayList<Double>();
        int count = 0;
        for (int i=0; i < ISFGlucose.size(); ++i) {
            double deviation = ISFGlucose.get(i).deviation;
            ISFdeviations.add(deviation);
            double BGI = ISFGlucose.get(i).BGI;
            BGIs.add(BGI);
            double avgDelta = ISFGlucose.get(i).AvgDelta;
            avgDeltas.add(avgDelta);
            double ratio = 1 + deviation / BGI;
            //log.debug("Deviation:",deviation,"BGI:",BGI,"avgDelta:",avgDelta,"ratio:",ratio);
            ratios.add(ratio);
            count++;
        }
        Collections.sort(avgDeltas);
        Collections.sort(BGIs);
        Collections.sort(ISFdeviations);
        Collections.sort(ratios);
        double p50deviation = IobCobCalculatorPlugin.percentile(ISFdeviations.toArray(new Double[ISFdeviations.size()]), 0.50);
        double p50BGI =  IobCobCalculatorPlugin.percentile(BGIs.toArray(new Double[BGIs.size()]), 0.50);
        double p50ratios = round(  IobCobCalculatorPlugin.percentile(ratios.toArray(new Double[ratios.size()]), 0.50),3);
        double fullNewISF = 0d;
        if (count < 10) {
            // leave ISF unchanged if fewer than 5 ISF data points
            fullNewISF = ISF;
        } else {
            // calculate what adjustments to ISF would have been necessary to bring median deviation to zero
            fullNewISF = ISF * p50ratios;
        }
        fullNewISF = round( fullNewISF,3);
        // adjust the target ISF to be a weighted average of fullNewISF and pumpISF
        double adjustmentFraction;
/*
        if (typeof(pumpProfile.autotune_isf_adjustmentFraction) !== 'undefined') {
            adjustmentFraction = pumpProfile.autotune_isf_adjustmentFraction;
        } else {*/
            adjustmentFraction = 1.0;
//        }

        // low autosens ratio = high ISF
        double maxISF = pumpISF / autotuneMin;
        // high autosens ratio = low ISF
        double minISF = pumpISF / autotuneMax;
        double adjustedISF = 0d;
        double newISF = 0d;
        if (pumpISF != 0) {
            if ( fullNewISF < 0 ) {
                adjustedISF = ISF;
                log.debug("fullNewISF < 0 setting adjustedISF to "+adjustedISF);
            } else {
                adjustedISF = adjustmentFraction*fullNewISF + (1-adjustmentFraction)*pumpISF;
            }
            // cap adjustedISF before applying 10%
            //log.debug(adjustedISF, maxISF, minISF);
            if (adjustedISF > maxISF) {
                log.debug("Limiting adjusted ISF of "+round(adjustedISF,2)+" to "+round(maxISF,2)+"(which is pump ISF of "+pumpISF+"/"+autotuneMin+")");
                adjustedISF = maxISF;
            } else if (adjustedISF < minISF) {
                log.debug("Limiting adjusted ISF of"+round(adjustedISF,2)+" to "+round(minISF,2)+"(which is pump ISF of "+pumpISF+"/"+autotuneMax+")");
                adjustedISF = minISF;
            }

            // TODO: philoul may be allow % of adjustments in settings with safety limits Check % (10% in original autotune OAPS for ISF ?)
            // and apply 20% of that adjustment
            newISF = ( 0.8 * ISF ) + ( 0.2 * adjustedISF );

            if (newISF > maxISF) {
                log.debug("Limiting ISF of"+round(newISF,2)+"to"+round(maxISF,2)+"(which is pump ISF of"+pumpISF+"/"+autotuneMin+")");
                newISF = maxISF;
            } else if (newISF < minISF) {
                log.debug("Limiting ISF of"+round(newISF,2)+"to"+round(minISF,2)+"(which is pump ISF of"+pumpISF+"/"+autotuneMax+")");
                newISF = minISF;
            }
        }
        newISF = Math.round( newISF * 1000 ) / 1000;
        //log.debug(avgRatio);
        //log.debug(newISF);
        p50deviation = Math.round( p50deviation * 1000 ) / 1000;
        p50BGI = Math.round( p50BGI * 1000 ) / 1000;
        adjustedISF = Math.round( adjustedISF * 1000 ) / 1000;
        log.debug("p50deviation: "+p50deviation+" p50BGI "+p50BGI+" p50ratios: "+p50ratios+" Old ISF: "+ISF+" fullNewISF: "+fullNewISF+" adjustedISF: "+adjustedISF+" newISF: "+newISF);

        if (newISF != 0d) {
            ISF = newISF;
        }


        // reconstruct updated version of previousAutotune as autotuneOutput
        JSONObject autotuneOutput = new JSONObject();
        if(previousAutotune != null)
            autotuneOutput = new JSONObject(previousAutotune.toString());
        autotuneOutput.put("basalProfile",  basalProfile.toString());
        //isfProfile.sensitivity = ISF;
        //autotuneOutput.put("isfProfile", isfProfile);
        autotuneOutput.put("sens", ISF);
        autotuneOutput.put("csf", CSF);
        //carbRatio = ISF / CSF;
        carbRatio = Math.round( carbRatio * 1000 ) / 1000;
        autotuneOutput.put("carb_ratio" , carbRatio);
        previousResult = new JSONObject(autotuneOutput.toString());
        return autotuneOutput.toString();
    }


    //Todo add profile selector in AutotuneFragment to allow running autotune plugin with other profiles than current
    public String result(int daysBack) {
        //todo add autotunePrep and autotuneCore in constructor (but I can't manage to do that, something probably wrong in dagger settings for these class ???)
        autotunePrep = new AutotunePrep(injector);
        autotuneCore = new AutotuneCore(injector);
        //clean autotune folder before run
        logString="";
        atLog("Start Autotune with " + daysBack + " days back");
        atLog("Delete Autotune files in " + AutotuneFS.SETTINGSFOLDER + " and " + AutotuneFS.AUTOTUNEFOLDER + " folders");
        AutotuneFS.deleteAutotuneFiles();

        long now = System.currentTimeMillis();
        lastRun = new Date(System.currentTimeMillis());
        // Today at 4 AM
        long endTime = DateUtil.toTimeMinutesFromMidnight(now, autotuneStartHour*60);
        // Check if 4 AM is before now
        if (endTime > now)
            endTime -= 24 * 60 * 60 * 1000L;
        long starttime = endTime - daysBack * 24 * 60 *  60 * 1000L;

        atLog("Create " +  AutotuneFS.SETTINGS + " file in " + AutotuneFS.SETTINGSFOLDER + " folder");
        AutotuneFS.createAutotunefile(AutotuneFS.SETTINGS,settings(lastRun,daysBack,new Date(starttime),new Date(endTime)),true);

        int tunedISF = 0;
        double isfResult = 0;
        basalsResultInit();
        profile = profileFunction.getProfile(now);
        TunedProfile tunedProfile = new TunedProfile(profileFunction.getProfile(now));
        if(tunedProfile.profile.equals(null))
            return null;
        tunedProfile.profilename=profileFunction.getProfileName();
        TunedProfile pumpprofile = new TunedProfile(profileFunction.getProfile(now));
        pumpprofile.profilename=profileFunction.getProfileName();

        AutotuneFS.createAutotunefile("pumpprofile.json", pumpprofile.profiletoOrefJSON(),true);
        AutotuneFS.createAutotunefile("pumpprofile.json", pumpprofile.profiletoOrefJSON());
        atLog("Create pumpprofile.json file in " + AutotuneFS.SETTINGSFOLDER + " and " + AutotuneFS.AUTOTUNEFOLDER + " folders");

        int toMgDl = 1;
        if(profileFunction.getUnits().equals("mmol"))
            toMgDl = 18;
        //log.debug("AAPS units: " + profileFunction.getUnits() +" so divisor is "+toMgDl);

        if(daysBack < 1){
            //todo add string
            return "Sorry I cannot do it for less than 1 day!";
        } else {
            for (int i = 0; i < daysBack; i++) {
                // get 24 hours BG values from 4 AM to 4 AM next day
                long from = starttime + i * 24 * 60 * 60 * 1000L;
                long to = from + 24 * 60 * 60 * 1000L;

                atLog("Tune day "+ i +" of "+ daysBack);

                //AutotuneIob contains BG and Treatments data from history (<=> query for ns-treatments and ns-entries)
                autotuneIob = new AutotuneIob(from, to);
                try {
                    //ns-entries files are for result compare with oref0 autotune on virtual machine
                    AutotuneFS.createAutotunefile("ns-entries." + AutotuneFS.formatDate(new Date(from)) + ".json", autotuneIob.glucosetoJSON().toString(2));
                    atLog("Create ns-entries." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folder");
                    //ns-treatments files are for result compare with oref0 autotune on virtual machine (include treatments ,tempBasal and extended
                    AutotuneFS.createAutotunefile("ns-treatments." + AutotuneFS.formatDate(new Date(from)) + ".json", autotuneIob.nsHistorytoJSON().toString(2).replace("\\/", "/"));
                    atLog("Create ns-treatments." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folder");
                } catch (JSONException e) {}

                //AutotunePrep autotunePrep = new AutotunePrep();
                preppedGlucose = autotunePrep.categorizeBGDatums(autotuneIob,tunedProfile, pumpprofile);
                AutotuneFS.createAutotunefile("aaps-autotune." + AutotuneFS.formatDate(new Date(from)) + ".json", preppedGlucose.toString(2));
                atLog("file aaps-autotune." + AutotuneFS.formatDate(new Date(from)) + ".json created in " + AutotuneFS.AUTOTUNEFOLDER + " folder");

                try {
                    tuneAllTheThings();
                } catch (JSONException e) {
                    log.error(e.getMessage());
                }

                tunedProfile = autotuneCore.tuneAllTheThings(preppedGlucose, tunedProfile, pumpprofile);

                AutotuneFS.createAutotunefile("newprofile." + AutotuneFS.formatDate(new Date(from)) + ".json", tunedProfile.profiletoOrefJSON());
                atLog("Create newprofile." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folders");
            }
        }

        if(previousResult != null) {
            String previousAutotune = previousResult.optString("basalProfile");
            previousAutotune = previousAutotune.substring(0, previousAutotune.length() - 1);
            previousAutotune = previousAutotune.substring(1, previousAutotune.length());
            List<String> basalProfile  = new ArrayList<String>(Arrays.asList(previousAutotune.split(", ")));
            List<Double> tunedBasalProfile = new ArrayList<Double>();
            //Parsing last result
            if(basalProfile.size() > 0) {
                for (int i = 0; i < 24; i++) {
                    tunedBasalProfile.add(Double.parseDouble(basalProfile.get(i)));
                }
            }
            DecimalFormat df = new DecimalFormat("0.000");
            String line = "------------------------------------------\n";


            result = line;
            result += "|Hour| Profile | Tuned |   %   |\n";
            result += line;
            for (int i = 0; i < 24; i++) {
                if(tunedBasalProfile.size() < i || tunedBasalProfile.size() == 0)
                    return "Error at index "+i+" or empty basalprofile<List>";
                String basalString = df.format(getBasal(i));

                String tunedString = df.format(tunedBasalProfile.get(i));
                int percentageChangeValue = (int) ((tunedBasalProfile.get(i)/getBasal(i)) * 100 - 100) ;
                String percentageChange;
                if (percentageChangeValue == 0)
                    percentageChange = "   0  ";
                else if (percentageChangeValue < 0)
                    percentageChange = " " + percentageChangeValue;
                else
                    percentageChange = "+" + percentageChangeValue;
                if (percentageChangeValue != 0)
                    percentageChange += "%";
                String hourString = i < 10 ? " 0"+ i : " " + i ;

                result += "| " + hourString + "  |  " + basalString + "  |  " +tunedString+"  |"+percentageChange+" |\n";

            }
            result += line;
            // show ISF CR and CSF
            result += "|  ISF |   "+ Round.roundTo(profile.getIsfMgdl()/toMgDl, 0.001) +"   |    "+ Round.roundTo(previousResult.optDouble("sens", 0d)/toMgDl,0.001)+"   |\n";
            result += line;
            result += "|  CR  |     "+profile.getIc()+"   |      "+round(previousResult.optDouble("carb_ratio", 0d),3)+"   |\n";
            result += line;
            result += "| CSF | "+ Round.roundTo(profile.getIsfMgdl() / profile.getIc() / toMgDl, 0.001)+"  |  "+Round.roundTo(previousResult.optDouble("csf", 0d)/toMgDl,0.001)+"  |\n";
            result += line;

            AutotuneFS.createAutotunefile(AutotuneFS.RECOMMENDATIONS,result);

            // trying to create new profile ready for switch
            JSONObject json = new JSONObject();
            JSONObject store = new JSONObject();
            JSONObject convertedProfile = new JSONObject();
            int basalIncrement = 60 * 60;

            try {
                json.put("defaultProfile", resourceHelper.gs(R.string.autotune_tunedprofile_name));
                json.put("store", store);
                convertedProfile.put("dia", profile.getDia());
                convertedProfile.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("carb_ratio", 0d))));
                convertedProfile.put("sens", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("sens", 0d))));
                JSONArray basals = new JSONArray();
                for (int h = 0; h < 24; h++) {
                    String time;
                    time = df.format(h) + ":00";
                    basals.put(new JSONObject().put("time", time).put("timeAsSeconds", h * basalIncrement).put("value", tunedBasalProfile.get(h)));
                };
                convertedProfile.put("basal", basals);
                convertedProfile.put("target_low", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", profile.getTargetLowMgdl())));
                convertedProfile.put("target_high", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", profile.getTargetHighMgdl())));
                convertedProfile.put("units", profileFunction.getUnits());

                AutotuneFS.createAutotunefile(AutotuneFS.profilName(null), convertedProfile.toString(4).replace("\\/","/"));

                store.put(resourceHelper.gs(R.string.autotune_tunedprofile_name), convertedProfile);
                //ProfileStore profileStore = new ProfileStore(json);
                //sp.putString("autotuneprofile", profileStore.getData().toString());
                //log.debug("Entered in ProfileStore "+profileStore.getSpecificProfile(MainApp.gs(R.string.tuneprofile_name)));
// TODO: check line below modified by philoul => need to be verify...
//                RxBus.INSTANCE.send(new EventProfileStoreChanged());
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }

            atLog("Create autotune Log file and zip");
            AutotuneFS.createAutotunefile("autotune." + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log", logString);
            // zip all autotune files created during the run.
            AutotuneFS.zipAutotune(lastRun);

            return result;
        } else
            return "No Result";
    }

    public static void basalsResultInit(){
        // initialize basalsResult if
//        log.debug(" basalsResult init");
        if(basalsResult.isEmpty()) {
            for (int i = 0; i < 24; i++) {
                basalsResult.add(0d);
            }
        } else {
            for (int i = 0; i < 24; i++) {
                basalsResult.set(i, 0d);
            }
        }
    }

    private String settings (Date runDate, int nbDays, Date firstloopstart, Date lastloopend) {
        String jsonString="";
        JSONObject jsonSettings = new JSONObject();
        InsulinInterface insulinInterface = activePlugin.getActiveInsulin();
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(runDate,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(runDate)).getTime()) / (60 * 1000));
        String startDateString = DateUtil.toISOString(firstloopstart,"yyyy-MM-dd",null);
        String endDateString = DateUtil.toISOString(new Date(lastloopend.getTime()-24*60*60*1000L),"yyyy-MM-dd",null);

        try {
            jsonSettings.put("datestring",DateUtil.toISOString(runDate,null,null));
            jsonSettings.put("dateutc",DateUtil.toISOString(runDate));
            jsonSettings.put("utcOffset",utcOffset);
            jsonSettings.put("units",profileFunction.getUnits());
            jsonSettings.put("timezone",TimeZone.getDefault().getID());
            jsonSettings.put("url_nightscout", sp.getString(R.string.key_nsclientinternal_url, ""));
            jsonSettings.put("nbdays", nbDays);
            jsonSettings.put("startdate",startDateString);
            jsonSettings.put("enddate",endDateString);
            // oref0_command is for running oref0-autotune on a virtual machine in a dedicated ~/aaps subfolder
            jsonSettings.put("oref0_command","oref0-autotune -d=~/aaps -n=" + sp.getString(R.string.key_nsclientinternal_url, "") + " -s="+startDateString+" -e=" + endDateString);
            // aaps_command is for running modified oref0-autotune with exported data from aaps (ns-entries and ns-treatment json files copied in ~/aaps/autotune folder and pumpprofile.json copied in ~/aaps/settings/
            jsonSettings.put("aaps_command","aaps-autotune -d=~/aaps -s="+startDateString+" -e=" + endDateString);
            jsonSettings.put("categorize_uam_as_basal",sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false));
            jsonSettings.put("tune_insulin_curve",false);
            if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
                jsonSettings.put("curve","ultra-rapid");
            else if (insulinInterface.getId() == InsulinInterface.OREF_RAPID_ACTING)
                jsonSettings.put("curve","rapid-acting");
            else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK) {
                jsonSettings.put("curve", "bilinear");
                jsonSettings.put("insulinpeaktime",sp.getInt(R.string.key_insulin_oref_peak,75));
            }

            jsonString = jsonSettings.toString(4).replace("\\/","/");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

        return jsonString;
    }

    public double averageProfileValue(Profile.ProfileValue[] pf) {
        double avgValue = 0;
        int secondPerDay=24*60*60;
        if (pf == null)
            return avgValue;
        for(int i = 0; i< pf.length;i++) {
            avgValue+=pf[i].value*((i==pf.length -1 ? secondPerDay : pf[i+1].timeAsSeconds) -pf[i].timeAsSeconds);
        }
        avgValue/=secondPerDay;
        return avgValue;
    }

    //todo replace round below by Round.roundTo function
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    // end of autotune Plugin
    public void atLog(String message){
        aapsLogger.debug(LTag.AUTOTUNE,message);
        log.debug(message);
        logString += message +"\n";

    }
}
