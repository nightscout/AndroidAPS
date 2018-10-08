package info.nightscout.androidaps.plugins.TuneProfile;

import android.content.res.Resources;
import android.support.v4.util.LongSparseArray;
import android.widget.TextView;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.NonOverlappingIntervals;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.events.EventProfileStoreChanged;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.db.BGDatum;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;


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

 */

public class TuneProfilePlugin extends PluginBase {
    // Turn on download of SGV and Treatments from NS or use local data
    private boolean useNSData = false;

    private static TuneProfilePlugin tuneProfile = null;
    private static Logger log = LoggerFactory.getLogger(TuneProfilePlugin.class);
    public static Profile profile;
    public static List<Double> basalsResult = new ArrayList<Double>();
    public static List<Treatment> treatments;
    private List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> UAMGlucoseData = new ArrayList<BGDatum>();
    private List<CRDatum> CRData = new ArrayList<CRDatum>();
    private JSONObject previousResult = null;
    private Profile tunedProfileResult;
    private String tunedProfileName = "Autotune";
    //copied from IobCobCalculator
    private static LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private static Intervals<TemporaryBasal> tempBasals = new NonOverlappingIntervals<>();
    private static Intervals<ExtendedBolus> extendedBoluses = new NonOverlappingIntervals<>();
    private NSService nsService = new NSService();

    public boolean nsDataDownloaded = false;

//    public TuneProfile() throws IOException {
//    }

    static TuneProfilePlugin tuneProfilePlugin;

    static public TuneProfilePlugin getPlugin() throws IOException{
        if (tuneProfilePlugin == null) {
            tuneProfilePlugin = new TuneProfilePlugin();
        }
        return tuneProfilePlugin;
    }

    public TuneProfilePlugin() throws IOException {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(TuneProfileFragment.class.getName())
                .pluginName(R.string.autotune)
                .shortName(R.string.autotune_shortname)
        );
    }

//    @Override
    public String getFragmentClass() {
        return TuneProfileFragment.class.getName();
    }


    @Override
    public String getName() { return "TuneProfile"; }

    @Override
    public String getNameShort() {
        String name = "TP";
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }


    public void invoke(String initiator, boolean allowNotification) {
        // invoke
    }


    public void getProfile(){
        //get active profile
        if (ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() == null){
            log.debug("TuneProfile: No profile selected");
            return;
        }
        profile = ProfileFunctions.getInstance().getProfile();
    }

    public static synchronized Double getBasal(Integer hour){
        try{
            getPlugin().getProfile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(profile.equals(null))
            return 0d;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }

    public static long roundUpTime(long time) {
        if (time % 60000 == 0)
            return time;
        long rouded = (time / 60000 + 1) * 60000;
        return rouded;
    }

    public IobTotal getCalculationToTimeTreatments(long time) {
        IobTotal total = new IobTotal(time);

        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return total;

        double dia = profile.getDia();

        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            if (!t.isValid) continue;
            if (t.date > time) continue;
            Iob tIOB = t.iobCalc(time, dia);
            total.iob += tIOB.iobContrib;
            total.activity += tIOB.activityContrib;
            if (t.date > total.lastBolusTime)
                total.lastBolusTime = t.date;
            if (!t.isSMB) {
                // instead of dividing the DIA that only worked on the bilinear curves,
                // multiply the time the treatment is seen active.
                long timeSinceTreatment = time - t.date;
                long snoozeTime = t.date + (long) (timeSinceTreatment * SP.getDouble("openapsama_bolussnooze_dia_divisor", 2.0));
                Iob bIOB = t.iobCalc(snoozeTime, dia);
                total.bolussnooze += bIOB.iobContrib;
            }
        }
        if (!ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses())
            synchronized (extendedBoluses) {
                for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc = e.iobCalc(time);
                    total.plus(calc);
                }
            }
        return total;
    }

    public IobTotal getCalculationToTimeTempBasals(long time) {
        IobTotal total = new IobTotal(time);
        synchronized (tempBasals) {
            for (Integer pos = 0; pos < tempBasals.size(); pos++) {
                TemporaryBasal t = tempBasals.get(pos);
                if (t.date > time)
                    continue;
                IobTotal calc = t.iobCalc(time, profile);
                log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
                total.plus(calc);
                if (!t.isEndingEvent()) {
                    total.lastTempDate = t.date;
                    total.lastTempDuration = t.durationInMinutes;
                    total.lastTempRate = t.tempBasalConvertedToAbsolute(t.date, profile);
                }

            }
        }
        if (ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            synchronized (extendedBoluses) {
                for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc = e.iobCalc(time);
                    totalExt.plus(calc);
                    TemporaryBasal t = new TemporaryBasal(e);
                    if (!t.isEndingEvent() && t.date > total.lastTempDate) {
                        total.lastTempDate = t.date;
                        total.lastTempDuration = t.durationInMinutes;
                        total.lastTempRate = t.tempBasalConvertedToAbsolute(t.date, profile);
                    }
                }
            }
            // Convert to basal iob
            totalExt.basaliob = totalExt.iob;
            totalExt.iob = 0d;
            totalExt.netbasalinsulin = totalExt.extendedBolusInsulin;
            totalExt.hightempinsulin = totalExt.extendedBolusInsulin;
            total.plus(totalExt);
        }
        return total;
    }


    public static IobTotal calculateFromTreatmentsAndTemps(long time) {
        long now = System.currentTimeMillis();
        time = roundUpTime(time);
        if (time < now && iobTable.get(time) != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return iobTable.get(time);
        } else {
            //log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        }
        IobTotal bolusIob = null;
        IobTotal basalIob = null;
        try {
            bolusIob = getPlugin().getCalculationToTimeTreatments(time).round();
            basalIob = getPlugin().getCalculationToTimeTempBasals(time).round();
        } catch (IOException e) {
            e.printStackTrace();
        }

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        if (time < System.currentTimeMillis()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    public void categorizeBGDatums(long from, long to) throws JSONException, ParseException, IOException {
        // TODO: Although the data from NS should be sorted maybe we need to sort it
        // sortBGdata
        // sort treatments
        log.debug("NSService should receive "+new Date(from)+" to "+new Date(to));
        //starting variable at 0
        //TODO: Add this to preferences
        boolean categorize_uam_as_basal = false;
        List<BgReading> sgv = new ArrayList<BgReading>();
        CSFGlucoseData = new ArrayList<BGDatum>();
        ISFGlucoseData = new ArrayList<BGDatum>();
        basalGlucoseData = new ArrayList<BGDatum>();
        UAMGlucoseData = new ArrayList<BGDatum>();
        CRData = new ArrayList<CRDatum>();


        int boluses = 0;
        int maxCarbs = 0;
        if (useNSData) {
            //glucosedata is sgv
            NSService nsService = new NSService();
            log.debug("(2)NSService should receive " + new Date(from) + " to " + new Date(to));
            nsService.setPeriod(from, to);
            nsService.execute();

            while (!nsService.jobFinished) {
                // wait for the background job to finish before asking for SGV
                nsDataDownloaded = nsService.jobFinished;
            }
            nsDataDownloaded = true;
            sgv = nsService.getSgv();
        } else {
            sgv = MainApp.getDbHelper().getBgreadingsDataFromTime(from, to, false);
        }

        if (sgv.size() < 1) {
            log.debug("No SGV data");
            return;
        }

        if (useNSData) {
            treatments = nsService.getTreatments();
        } else {
            treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();
        }

        log.debug("Treatmets size: "+treatments.size());
        //trim treatments size
        for (int i=0;i<treatments.size();i++){
            if(treatments.get(i).date<from || treatments.get(i).date>to){
                treatments.remove(i);
            }
        }
        /*} catch (IOException e) {
            e.printStackTrace();
        }*/
        if (treatments.size() < 1) {
            log.debug("No treatments");
            return;
        }


        if(profile == null)
            getProfile();
        JSONObject IOBInputs = new JSONObject();
        IOBInputs.put("profile", profile);
        IOBInputs.put("history", "pumpHistory");


        List<BGDatum> bucketedData = new ArrayList<BGDatum>();

        double CRInitialIOB = 0d;
        double CRInitialBG = 0d;
        Date CRInitialCarbTime = null;

        // BasalGlucosedata is null
//        bucketedData.add(basalGlucoseData.get(0));
        int j = 0;
        //for loop to validate and bucket the data
        for (int i=1; i < sgv.size(); ++i) {
            long BGTime = 0;
            long lastBGTime = 0 ;
            if (sgv.get(i).date != 0 ) {
                BGTime = new Date(sgv.get(i).date).getTime();
            } /* We don't need these checks as we get the glucose from NS

            else if (sgv.get(i).displayTime) {
                BGTime = new Date(glucoseData[i].displayTime.replace('T', ' '));
            } else if (glucoseData[i].dateString) {
                BGTime = new Date(glucoseData[i].dateString);
            } else { log.debug("Could not determine BG time"); }
            if (glucoseData[i-1].date) {
                lastBGTime = new Date(glucoseData[i-1].date);
            } else if (glucoseData[i-1].displayTime) {
                lastBGTime = new Date(glucoseData[i-1].displayTime.replace('T', ' '));
            } else if (glucoseData[i-1].dateString) {
                lastBGTime = new Date(glucoseData[i-1].dateString);*/
            else { log.error("Could not determine last BG time"); }
            if(i>1) {
                if (sgv.get(i).value < 39 || sgv.get(i - 1).value < 39) {
                    continue;
                }
            } else if (sgv.get(i).value < 39) {
                continue;
            }
            long elapsedMinutes = (BGTime - lastBGTime)/(60*1000);
            if(Math.abs(elapsedMinutes) > 2) {
                j++;
                if(bucketedData.size()<j)
                    bucketedData.add(new BGDatum(sgv.get(i)));
                else
                    bucketedData.set(j,new BGDatum(sgv.get(i)));
//                bucketedData<j>.date = BGTime;
                /*if (! bucketedData[j].dateString) {
                    bucketedData[j].dateString = BGTime.toISOString();
                }*/
            } else {
                // if duplicate, average the two
                BgReading average = new BgReading();
                average.copyFrom(bucketedData.get(j));
                average.value = (bucketedData.get(j).value + sgv.get(i).value)/2;
                bucketedData.set(j, new BGDatum(average));
            }
        }
        // go through the treatments and remove any that are older than the oldest glucose value
        //log.debug(treatments);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss''");
        log.debug("Treatments size before clear: "+treatments.size());
        log.debug("Treatment(0) "+new Date(treatments.get(0).date).toString()+" last "+new Date(treatments.get(treatments.size()-1).date).toString());
        for (int i=treatments.size()-1; i>0; --i) {
            Treatment treatment = treatments.get(i);
            //log.debug(treatment);
            if (treatment != null) {
                Date treatmentDate = new Date(treatment.date);
                long treatmentTime = treatmentDate.getTime();
                BgReading glucoseDatum = bucketedData.get(bucketedData.size()-1);
                //log.debug(glucoseDatum);
                Date BGDate = new Date(glucoseDatum.date);
                long BGTime = BGDate.getTime();

//                log.debug("Oldest BG data is: "+format.format(BGDate)+" oldest treatment is: "+format.format(new Date(treatments.get(treatments.size()-1).date).toGMTString()));
                if ( treatmentTime < BGTime ) {
                    //treatments.splice(i,1);
                    treatments.remove(i);
                }
//                log.debug("Treatment size after removing of older: "+treatments.size());
            }
        }
        //log.debug(treatments);

        boolean calculatingCR = false;
        int absorbing = 0;
        int uam = 0; // unannounced meal
        double mealCOB = 0d;
        int mealCarbs = 0;
        int CRCarbs = 0;
        String type = "";
        // main for loop
        List<Treatment> fullHistory = new ArrayList<Treatment>();//IOBInputs.history; TODO: get treatments with IOB
        double glucoseDatumAvgDelta = 0d;
        double glucoseDatumDelta = 0d;
        for (int i=bucketedData.size()-5; i > 0; --i) {
            BGDatum glucoseDatum = bucketedData.get(i);
            glucoseDatumAvgDelta = 0d;
            glucoseDatumDelta = 0d;
            //log.debug(glucoseDatum);
            Date BGDate = new Date(glucoseDatum.date);
            long BGTime = BGDate.getTime();
            int myCarbs = 0;
            // As we're processing each data point, go through the treatment.carbs and see if any of them are older than
            // the current BG data point.  If so, add those carbs to COB.
//            log.debug("Categorize 1-1 - Doing it for "+BGDate.toString()+" trSize is "+treatments.size());
            for (int jj=0; jj < treatments.size()-1; jj++) {
                Treatment treatment = treatments.get(jj);
                if (treatment != null) {
                    Date treatmentDate = new Date(treatment.date);
                    long treatmentTime = treatmentDate.getTime();
                    //log.debug(treatmentDate);
//                    log.debug("Categorize 1-2 Treatment at position(" + (treatments.size() - 1) + ") diff is " + new Date(BGTime).toGMTString() + " trDate " + new Date(treatmentTime).toGMTString());
                    if (treatmentTime < BGTime) {
                        if (treatment.carbs >= 1) {
                            //                        Here I parse Integers not float like the original source categorize.js#136
                            log.debug("Categorize 1-3 Adding carbs: " + treatment.carbs + " for date " + new Date(treatment.date).toLocaleString());
                            mealCOB += (int) (treatment.carbs);
                            mealCarbs += (int) (treatment.carbs);
                            myCarbs = (int) treatment.carbs;

                        } //else
                        //                            log.debug("Treatment date: "+new Date(treatmentTime).toString()+" but BGTime: "+new Date(BGTime).toString());
//                        treatments.remove(treatments.size() - 1);
                        treatments.remove(jj);
                    }
                }
            }
            double BG=0;
            double avgDelta = 0;
            double delta;
            // TODO: re-implement interpolation to avoid issues here with gaps
            // calculate avgDelta as last 4 datapoints to better catch more rises after COB hits zero
//            log.debug("Categorize 2");
            if (bucketedData.get(i).value != 0 && bucketedData.get(i+4).value != 0) {
                //log.debug(bucketedData[i]);
                BG = bucketedData.get(i).value;
                if ( BG < 40 || bucketedData.get(i+4).value < 40) {
                    //process.stderr.write("!");
                    continue;
                }
                avgDelta = (BG - bucketedData.get(i+4).value)/4;
                delta = (BG - bucketedData.get(i+4).value);
            } else { log.error("Could not find glucose data"); }

            avgDelta = avgDelta*100 / 100;
            glucoseDatum.AvgDelta = avgDelta;

            //sens = ISF
//            int sens = ISF.isfLookup(IOBInputs.profile.isfProfile,BGDate);
            double sens = profile.getIsf(BGTime);
//            IOBInputs.clock=BGDate.toString();
            // trim down IOBInputs.history to just the data for 6h prior to BGDate
            //log.debug(IOBInputs.history[0].created_at);
            List<Treatment> newHistory = null;
            for (int h=0; h<fullHistory.size(); h++) {
//                Date hDate = new Date(fullHistory.get(h).created_at) TODO: When we get directly from Ns there should be created_at
                Date hDate = new Date(fullHistory.get(h).date);
                //log.debug(fullHistory[i].created_at, hDate, BGDate, BGDate-hDate);
                //if (h == 0 || h == fullHistory.length - 1) {
                //log.debug(hDate, BGDate, hDate-BGDate)
                //}
                if (BGDate.getTime()-hDate.getTime() < 6*60*60*1000 && BGDate.getTime()-hDate.getTime() > 0) {
                    //process.stderr.write("i");
                    //log.debug(hDate);
                    newHistory.add(fullHistory.get(h));
                }
            }
            if(newHistory != null)
                IOBInputs = new JSONObject(newHistory.toString());
            else
                IOBInputs = new JSONObject();
            // process.stderr.write("" + newHistory.length + " ");
            //log.debug(newHistory[0].created_at,newHistory[newHistory.length-1].created_at,newHistory.length);


            // for IOB calculations, use the average of the last 4 hours' basals to help convergence;
            // this helps since the basal this hour could be different from previous, especially if with autotune they start to diverge.
            // use the pumpbasalprofile to properly calculate IOB during periods where no temp basal is set
            Calendar calendar = GregorianCalendar.getInstance(); // creates a new calendar instance
            calendar.setTime(BGDate);   // assigns calendar to given date
            int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h format
//            double currentPumpBasal = basal.basalLookup(opts.pumpbasalprofile, BGDate);
            double currentPumpBasal = TuneProfilePlugin.getBasal(hourOfDay);
            double basal1hAgo = TuneProfilePlugin.getBasal(hourOfDay - 1);
            double basal2hAgo = TuneProfilePlugin.getBasal(hourOfDay - 2);
            double basal3hAgo = TuneProfilePlugin.getBasal(hourOfDay - 3);
            if(hourOfDay>3){
                basal1hAgo = TuneProfilePlugin.getBasal(hourOfDay - 1);
                basal2hAgo = TuneProfilePlugin.getBasal(hourOfDay - 2);
                basal3hAgo = TuneProfilePlugin.getBasal(hourOfDay - 3);
            } else {
                if(hourOfDay-1 < 0)
                    basal1hAgo = TuneProfilePlugin.getBasal(24 - 1);
                else
                    basal1hAgo = TuneProfilePlugin.getBasal(hourOfDay - 1);
                if(hourOfDay-2 < 0)
                    basal2hAgo = TuneProfilePlugin.getBasal(24 - 2);
                else
                    basal2hAgo = TuneProfilePlugin.getBasal(hourOfDay - 2);
                if(hourOfDay-3 < 0)
                    basal3hAgo = TuneProfilePlugin.getBasal(24 - 3);
                else
                    basal3hAgo = TuneProfilePlugin.getBasal(hourOfDay - 3);
            }
            double sum = currentPumpBasal+basal1hAgo+basal2hAgo+basal3hAgo;
//            IOBInputs.profile.currentBasal = Math.round((sum/4)*1000)/1000;

            // this is the current autotuned basal, used for everything else besides IOB calculations
            double currentBasal = TuneProfilePlugin.getBasal(hourOfDay);

            //log.debug(currentBasal,basal1hAgo,basal2hAgo,basal3hAgo,IOBInputs.profile.currentBasal);
            // basalBGI is BGI of basal insulin activity.
            double basalBGI = Math.round(( currentBasal * sens / 60 * 5 )*100)/100; // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
            //console.log(JSON.stringify(IOBInputs.profile));
            // call iob since calculated elsewhere
            IobTotal iob = TuneProfilePlugin.calculateFromTreatmentsAndTemps(BGDate.getTime());
            //log.debug(JSON.stringify(iob));

            // activity times ISF times 5 minutes is BGI
            double BGI = Math.round(( -iob.activity * sens * 5 )*100)/100;
            // datum = one glucose data point (being prepped to store in output)
            glucoseDatum.BGI = BGI;
            // calculating deviation
            double deviation = avgDelta-BGI;

            // set positive deviations to zero if BG is below 80
            if ( BG < 80 && deviation > 0 ) {
                deviation = 0;
            }

            // rounding and storing deviation
            deviation = round(deviation,2);
            glucoseDatum.deviation = deviation;


            // Then, calculate carb absorption for that 5m interval using the deviation.
            if ( mealCOB > 0 ) {
                Profile profile;
                if (ProfileFunctions.getInstance().getProfile() == null){
                    log.debug("No profile selected");
                    return;
                }
                profile = ProfileFunctions.getInstance().getProfile();
                double ci = Math.max(deviation, SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
                double absorbed = ci * profile.getIc() / sens;
                // Store the COB, and use it as the starting point for the next data point.
                mealCOB = Math.max(0, mealCOB-absorbed);
            }


            // Calculate carb ratio (CR) independently of CSF and ISF
            // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
            // For now, if another meal IOB/COB stacks on top of it, consider them together
            // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
            // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.
            if (mealCOB > 0 || calculatingCR ) {
                // set initial values when we first see COB
                CRCarbs += myCarbs;
                if (calculatingCR == false) {
                    CRInitialIOB = iob.iob;
                    CRInitialBG = glucoseDatum.value;
                    CRInitialCarbTime = new Date(glucoseDatum.date);
                    log.debug("CRInitialIOB: "+CRInitialIOB+" CRInitialBG: "+CRInitialBG+" CRInitialCarbTime: "+CRInitialCarbTime.toString());
                }
                // keep calculatingCR as long as we have COB or enough IOB
                if ( mealCOB > 0 && i>1 ) {
                    calculatingCR = true;
                } else if ( iob.iob > currentBasal/2 && i>1 ) {
                    calculatingCR = true;
                    // when COB=0 and IOB drops low enough, record end values and be done calculatingCR
                } else {
                    double CREndIOB = iob.iob;
                    double CREndBG = glucoseDatum.value;
                    Date CREndTime = new Date(glucoseDatum.date);
                    log.debug("CREndIOB: "+CREndIOB+" CREndBG: "+CREndBG+" CREndTime: "+CREndTime);
                    //TODO:Fix this one as it produces error
//                    JSONObject CRDatum = new JSONObject("{\"CRInitialIOB\": "+CRInitialIOB+",   \"CRInitialBG\": "+CRInitialBG+",   \"CRInitialCarbTime\": "+CRInitialCarbTime+",   \"CREndIOB\": " +CREndIOB+",   \"CREndBG\": "+CREndBG+",   \"CREndTime\": "+CREndTime+                            ",   \"CRCarbs\": "+CRCarbs+"}");
                    String crDataString = "{\"CRInitialIOB\": "+CRInitialIOB+",   \"CRInitialBG\": "+CRInitialBG+",   \"CRInitialCarbTime\": "+CRInitialCarbTime.getTime()+",   \"CREndIOB\": " +CREndIOB+",   \"CREndBG\": "+CREndBG+",   \"CREndTime\": "+CREndTime.getTime()+",   \"CRCarbs\": "+CRCarbs+"}";
//                    log.debug("CRData is: "+crDataString);
                    CRDatum crDatum = new CRDatum();
                    crDatum.CRInitialBG = CRInitialBG;
                    crDatum.CRInitialIOB = CRInitialIOB;
                    crDatum.CRInitialCarbTime = CRInitialCarbTime.getTime();
                    crDatum.CREndBG = CREndBG;
                    crDatum.CREndIOB = CREndIOB;
                    crDatum.CREndTime = CREndTime.getTime();
                    //log.debug(CRDatum);

                    int CRElapsedMinutes = Math.round((CREndTime.getTime() - CRInitialCarbTime.getTime()) / (1000 * 60));
                    //log.debug(CREndTime - CRInitialCarbTime, CRElapsedMinutes);
                    if ( CRElapsedMinutes < 60 || ( i==1 && mealCOB > 0 ) ) {
                        log.debug("Ignoring "+CRElapsedMinutes+" m CR period.");
                    } else {
                        CRData.add(crDatum);
                    }

                    CRCarbs = 0;
                    calculatingCR = false;
                }
            }


            // If mealCOB is zero but all deviations since hitting COB=0 are positive, assign those data points to CSFGlucoseData
            // Once deviations go negative for at least one data point after COB=0, we can use the rest of the data to tune ISF or basals
            if (mealCOB > 0 || absorbing != 0 || mealCarbs > 0) {
                // if meal IOB has decayed, then end absorption after this data point unless COB > 0
                if ( iob.iob < currentBasal/2 ) {
                    absorbing = 0;
                    // otherwise, as long as deviations are positive, keep tracking carb deviations
                } else if (deviation > 0) {
                    absorbing = 1;
                } else {
                    absorbing = 0;
                }
                if ( absorbing != 0 && mealCOB != 0) {
                    mealCarbs = 0;
                }
                // check previous "type" value, and if it wasn't csf, set a mealAbsorption start flag
                //log.debug(type);
                if ( type.equals("csf") == false ) {
                    glucoseDatum.mealAbsorption = "start";
                    log.debug(glucoseDatum.mealAbsorption+" carb absorption");
                }
                type="csf";
                glucoseDatum.mealCarbs = mealCarbs;
                //if (i == 0) { glucoseDatum.mealAbsorption = "end"; }
                CSFGlucoseData.add(glucoseDatum);
            } else {
                // check previous "type" value, and if it was csf, set a mealAbsorption end flag
                if ( type == "csf" ) {
                    CSFGlucoseData.get(CSFGlucoseData.size()-1).mealAbsorption = "end";
                    log.debug(CSFGlucoseData.get(CSFGlucoseData.size()-1).mealAbsorption+" carb absorption");
                }

                if ((iob.iob > currentBasal || deviation > 6 || uam > 0) ) {
                    if (deviation > 0) {
                        uam = 1;
                    } else {
                        uam = 0;
                    }
                    if ( type != "uam" ) {
                        glucoseDatum.uamAbsorption = "start";
                        log.debug(glucoseDatum.uamAbsorption+" unannnounced meal absorption");
                    }
                    type="uam";
                    UAMGlucoseData.add(glucoseDatum);
                } else {
                    if ( type == "uam" ) {
                        log.debug("end unannounced meal absorption");
                    }


                    // Go through the remaining time periods and divide them into periods where scheduled basal insulin activity dominates. This would be determined by calculating the BG impact of scheduled basal insulin (for example 1U/hr * 48 mg/dL/U ISF = 48 mg/dL/hr = 5 mg/dL/5m), and comparing that to BGI from bolus and net basal insulin activity.
                    // When BGI is positive (insulin activity is negative), we want to use that data to tune basals
                    // When BGI is smaller than about 1/4 of basalBGI, we want to use that data to tune basals
                    // When BGI is negative and more than about 1/4 of basalBGI, we can use that data to tune ISF,
                    // unless avgDelta is positive: then that's some sort of unexplained rise we don't want to use for ISF, so that means basals
                    if (basalBGI > -4 * BGI) {
                            // attempting to prevent basal from being calculated as negative; should help prevent basals from going below 0
                            //var minPossibleDeviation = -( basalBGI + Math.max(0,BGI) );
                            //var minPossibleDeviation = -basalBGI;
                            //if ( deviation < minPossibleDeviation ) {
                            //console.error("Adjusting deviation",deviation,"to",minPossibleDeviation.toFixed(2));
                            //deviation = minPossibleDeviation;
                            //deviation = deviation.toFixed(2);
                            //glucoseDatum.deviation = deviation;
                            //}
                        type="basal";
                        basalGlucoseData.add(glucoseDatum);
                    } else {
                        if ( avgDelta > 0 && avgDelta > -2*BGI ) {
                            //type="unknown"
                            type="basal";
                            basalGlucoseData.add(glucoseDatum);
                        } else {
                            type="ISF";
                            ISFGlucoseData.add(glucoseDatum);
                        }
                    }
                }
            }
            // debug line to print out all the things
//            BGDateArray = BGDate.toString().split(" ");
//            BGTime = BGDateArray[4];
//            log.debug(absorbing+" mealCOB: "+mealCOB+" mealCarbs: "+mealCarbs+" basalBGI: "+round(basalBGI,1)+" BGI: "+BGI+" IOB: "+iob.iob+" at "+new Date(BGTime).toString()+" dev: "+deviation+" avgDelta: "+avgDelta +" "+ type);
        }

//        try {
            List<Treatment> treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        BgReading CRDatum;
        /*CRData.forEach(function(CRDatum) {
            JSONObject dosedOpts = {
                    "treatments": treatments
                    , "profile": profile
                    , "start": CRDatum.CRInitialCarbTime
                    , "end": CRDatum.CREndTime};
        */
        // TODO: What this one does ?!?
        //double insulinDosed = dosed(dosedOpts);
        //CRDatum.CRInsulin = insulinDosed.insulin;
        //log.debug(CRDatum);});

        int CSFLength = CSFGlucoseData.size();
        int ISFLength = ISFGlucoseData.size();
        int UAMLength = UAMGlucoseData.size();
        int basalLength = basalGlucoseData.size();
        if (categorize_uam_as_basal) {
            log.debug("Categorizing all UAM data as basal.");
            basalGlucoseData.addAll(UAMGlucoseData);
        } else if (2*basalLength < UAMLength) {
            //log.debug(basalGlucoseData, UAMGlucoseData);
            log.debug("Warning: too many deviations categorized as UnAnnounced Meals");
            log.debug("Adding",UAMLength,"UAM deviations to",basalLength,"basal ones");
            basalGlucoseData.addAll(UAMGlucoseData);
            //log.debug(basalGlucoseData);
            // if too much data is excluded as UAM, add in the UAM deviations, but then discard the highest 50%
            // Todo: Try to sort it here
            /*basalGlucoseData.sort(function (a, b) {
                return a.deviation - b.deviation;
            });*/
            List<BGDatum> newBasalGlucose = new ArrayList<BGDatum>();;
            for(int i=0;i < basalGlucoseData.size()/2;i++){
                newBasalGlucose.add(basalGlucoseData.get(i));
            }
            //log.debug(newBasalGlucose);
            basalGlucoseData = newBasalGlucose;
            log.debug("and selecting the lowest 50%, leaving"+ basalGlucoseData.size()+"basal+UAM ones");

            log.debug("Adding "+UAMLength+" UAM deviations to "+ISFLength+" ISF ones");
            ISFGlucoseData.addAll(UAMGlucoseData);
            //log.debug(ISFGlucoseData.length, UAMLength);
        }
        basalLength = basalGlucoseData.size();
        ISFLength = ISFGlucoseData.size();
        if ( 4*basalLength + ISFLength < CSFLength && ISFLength < 10 ) {
            log.debug("Warning: too many deviations categorized as meals");
            //log.debug("Adding",CSFLength,"CSF deviations to",basalLength,"basal ones");
            //var basalGlucoseData = basalGlucoseData.concat(CSFGlucoseData);
            log.debug("Adding",CSFLength,"CSF deviations to",ISFLength,"ISF ones");
            for(int ii = 0; ii< CSFGlucoseData.size()-1;ii++) {
                ISFGlucoseData.add(CSFGlucoseData.get(ii));
            }
            CSFGlucoseData = new ArrayList<>();
        }

        log.debug("CRData: "+CRData.size());
        log.debug("CSFGlucoseData: "+CSFGlucoseData.size());
        log.debug("ISFGlucoseData: "+ISFGlucoseData.size());
        log.debug("BasalGlucoseData: "+basalGlucoseData.size());
//        String returnJSON = "{\"CRData\":"+CRData.toString()+",\"CSFGlucoseData\": "+CSFGlucoseData.toString()+",\"ISFGlucoseData\": "+ISFGlucoseData.toString()+",\"basalGlucoseData\": "+basalGlucoseData.toString()+"}";
//        log.debug("Returning: "+returnJSON);
        return;
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
        double autotuneMax = SafeParse.stringToDouble(SP.getString("openapsama_autosens_max", "1.2"));
        double autotuneMin = SafeParse.stringToDouble(SP.getString("openapsama_autosens_min", "0.7"));
        double min5minCarbImpact = SP.getDouble("openapsama_min_5m_carbimpact", 3.0);
        //log.debug(pumpBasalProfile);
//        var basalProfile = previousAutotune.basalprofile;
        //log.debug(basalProfile);
//        var isfProfile = previousAutotune.isfProfile;
        //log.debug(isfProfile);
        int toMgDl = 1;
        if(profile.equals(null))
            return null;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        double ISF = profile.getIsf()*toMgDl;

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
        if (pumpISFProfile != null && pumpISFProfile.getIsf() != 0d) {
            pumpISF = pumpISFProfile.getIsf()*toMgDl;
            pumpCarbRatio = pumpProfile.getIc();
            pumpCSF = pumpISF / pumpCarbRatio;
            log.debug("After getting pumpProfile: pumpISF is "+pumpISF+" pumpCSF "+pumpCSF+" pumpCarbRatio "+pumpCarbRatio);
        }
        if (carbRatio == 0d) { carbRatio = pumpCarbRatio; }
        if (CSF == 0d) { CSF = pumpCSF; }
        if (ISF == 0d) { ISF = pumpISF; }
        //log.debug(CSF);
//        List<BGDatum> preppedGlucose = preppedGlucose;
        List<BGDatum> CSFGlucose = CSFGlucoseData;
        //log.debug(CSFGlucose[0]);
        List<BGDatum> ISFGlucose = ISFGlucoseData;
        //log.debug(ISFGlucose[0]);
        List<BGDatum> basalGlucose = basalGlucoseData;
        //log.debug(basalGlucose[0]);
        List<CRDatum> CRData = this.CRData;
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
                double CRBGChange = CRData.get(i).CREndBG - CRData.get(i).CRInitialBG;
                double CRInsulinReq = CRBGChange / ISF;
                double CRIOBChange = CRData.get(i).CREndIOB - CRData.get(i).CRInitialIOB;
                CRData.get(i).CRInsulinTotal = CRData.get(i).CRInitialIOB + CRData.get(i).CRInsulin + CRInsulinReq;
                //log.debug(CRDatum.CRInitialIOB, CRDatum.CRInsulin, CRInsulinReq, CRInsulinTotal);
                double CR = Math.round(CRData.get(i).CRCarbs / CRData.get(i).CRInsulinTotal * 1000) / 1000;
                //log.debug(CRBGChange, CRInsulinReq, CRIOBChange, CRInsulinTotal);
                //log.debug("CRCarbs:",CRDatum.CRCarbs,"CRInsulin:",CRDatum.CRInsulinTotal,"CR:",CR);
                if (CRData.get(i).CRInsulin > 0) {
                    CRTotalCarbs += CRData.get(i).CRCarbs;
                    CRTotalInsulin += CRData.get(i).CRInsulinTotal;
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

    public String result(int daysBack) throws IOException, ParseException {

        int tunedISF = 0;
        double isfResult = 0;
        basalsResultInit();
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now - ((daysBack-1) * 24 * 60 * 60 * 1000L));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        // midnight
        long endTime = c.getTimeInMillis();
        long starttime = endTime - (24 * 60 * 60 * 1000L);
//        Date lastProfileChange = NSService.lastProfileChange();
        Date lastProfileChange = new Date(TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(endTime).date);
        int toMgDl = 1;
        getProfile();
        if(profile.equals(null))
            return null;
        tunedProfileResult = profile;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        log.debug("ActiveProfile units: " + profile.getUnits()+" so divisor is "+toMgDl);
        //Check if Wifi is Connected
//        if(!nsService.isWifiConnected()){
           // return "READ THE WARNING!";
//        }
        // check if daysBack goes before the last profile switch
        if((System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)) < lastProfileChange.getTime()){
            //return "ERROR -> I cannot tune before the last profile switch!("+(System.currentTimeMillis() - lastProfileChange.getTime()) / (24 * 60 * 60 * 1000L)+" days ago)";
        }
        if(daysBack < 1){
            return "Sorry I cannot do it for less than 1 day!";
        } else {
            for (int i = daysBack; i > 0; i--) {
//                tunedBasalsInit();
                long timeBack = (i-1) * 24 * 60 * 60 * 1000L;
                try {
                    log.debug("Day "+i+" of "+daysBack);
                    log.debug("NSService asked for data from "+formatDate(new Date(starttime))+" \nto "+formatDate(new Date(endTime)));
                    categorizeBGDatums(starttime-timeBack, endTime-timeBack);
//                    categorizeBGDatums(starttime, endTime);
                    tuneAllTheThings();
                } catch (JSONException e) {
                    log.error(e.getMessage());
                }
            }
        }
        if(previousResult != null) {
            String previousAutotune = previousResult.optString("basalProfile");
            previousAutotune = previousAutotune.substring(0, previousAutotune.length() - 1);
            previousAutotune = previousAutotune.substring(1, previousAutotune.length());
            List<String> basalProfile  = new ArrayList<String>(Arrays.asList(previousAutotune.split(", ")));
            List<Double> tunedProfile = new ArrayList<Double>();
            //Parsing last result
            if(basalProfile.size() > 0) {
                for (int i = 0; i < 24; i++) {
                    tunedProfile.add(Double.parseDouble(basalProfile.get(i)));
                }
            }
            DecimalFormat df = new DecimalFormat("0.000");
            String line = "----------------------------------------------------\n";
            String result = line;
            result += "| Hour | Profile | Autotune |  %  |\n";
            result += line;
            for (int i = 0; i < 24; i++) {
                if(tunedProfile.size() < i || tunedProfile.size() == 0)
                    return "Error at index "+i+" or empty basalprofile<List>";
                String basalString = df.format(getBasal(i));

                String tunedString = df.format(tunedProfile.get(i));
                int percentageChangeValue = (int) ((tunedProfile.get(i)/getBasal(i)) * 100 - 100) ;
                String percentageChange;
                if (percentageChangeValue <= 0)
                    percentageChange = "" + percentageChangeValue;
                else
                    percentageChange = "+" + percentageChangeValue;
                if (percentageChangeValue != 0)
                    percentageChange += "%";
                String hourString = ""+i;
                if(i<10)
                    hourString = i+"  ";
                result += "|   " + hourString + "    |  " + basalString + "  |    " +tunedString+"    | "+percentageChange+" |\n";

            }
            result += line;
            // show ISF CR and CSF
            result += "|  ISF    |    "+round(profile.getIsf(), 3) +"    |    "+round(previousResult.optDouble("sens", 0d)/toMgDl,3)+"     |\n";
            result += line;
            result += "|   CR   |    "+profile.getIc()+"    |    "+round(previousResult.optDouble("carb_ratio", 0d),3)+"     |\n";
            result += line;
            result += "|  CSF  |    "+round(profile.getIsf()/profile.getIc(),3)+"    |    "+round(previousResult.optDouble("csf", 0d)/toMgDl,3)+"     |\n";
            result += line;

            // trying to create new profile ready for switch
            JSONObject json = new JSONObject();
            JSONObject store = new JSONObject();
            JSONObject convertedProfile = new JSONObject();
            int basalIncrement = 60 * 60;

            try {
                json.put("defaultProfile", MainApp.gs(R.string.tuneprofile_name));
                json.put("store", store);
                convertedProfile.put("dia", profile.getDia());
                convertedProfile.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("carb_ratio", 0d))));
                convertedProfile.put("sens", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("sens", 0d)/toMgDl)));
                JSONArray basals = new JSONArray();
                for (int h = 0; h < 24; h++) {
                    String time;
                    time = df.format(h) + ":00";
                    basals.put(new JSONObject().put("time", time).put("timeAsSeconds", h * basalIncrement).put("value", tunedProfile.get(h)));
                };
                convertedProfile.put("basal", basals);
                convertedProfile.put("target_low", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", profile.getTargetLow())));
                convertedProfile.put("target_high", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", profile.getTargetHigh())));
                convertedProfile.put("units", profile.getUnits());
                store.put(MainApp.gs(R.string.tuneprofile_name), convertedProfile);
                ProfileStore profileStore = new ProfileStore(json);
                SP.putString("autotuneprofile", profileStore.getData().toString());
                log.debug("Entered in ProfileStore "+profileStore.getSpecificProfile(MainApp.gs(R.string.tuneprofile_name)));
                MainApp.bus().post(new EventProfileStoreChanged());
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }

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

    public String formatDate(Date date){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(date);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    // end of TuneProfile Plugin
}
