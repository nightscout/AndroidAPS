package info.nightscout.androidaps.plugins.general.autotune;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.autotune.data.BGDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.CRDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose;
import info.nightscout.androidaps.plugins.general.autotune.data.TunedProfile;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import info.nightscout.androidaps.utils.Round;


@Singleton
public class AutotunePrep {
    private boolean useNSData = false;
    public boolean nsDataDownloaded = false;
    private static Logger log = LoggerFactory.getLogger(AutotunePlugin.class);
    @Inject ProfileFunction profileFunction;
    @Inject AutotunePlugin autotunePlugin;
    @Inject SP sp;
    @Inject IobCobCalculatorPlugin iobCobCalculatorPlugin;
    @Inject TreatmentsPlugin treatmentsPlugin;
    private final HasAndroidInjector injector;
    private AutotuneIob autotuneIob;


    @Inject
    public AutotunePrep(
            HasAndroidInjector injector
    ) {
        this.injector=injector;
        this.injector.androidInjector().inject(this);
    }

    public PreppedGlucose categorizeBGDatums(long from, long to, TunedProfile tunedprofile)  {
        autotuneIob = new AutotuneIob(from,to);

        try {
            //ns-entries files are for result compare with oref0 autotune on virtual machine
            AutotuneFS.createAutotunefile("ns-entries." + AutotuneFS.formatDate(new Date(from)) + ".json", autotuneIob.glucosetoJSON().toString(2));
            autotunePlugin.atLog("Create ns-entries." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folder");
            //ns-treatments files are for result compare with oref0 autotune on virtual machine (include treatments ,tempBasal and extended
            AutotuneFS.createAutotunefile("ns-treatments." + AutotuneFS.formatDate(new Date(from)) + ".json", autotuneIob.nsHistorytoJSON().toString(2).replace("\\/", "/"));
            autotunePlugin.atLog("Create ns-treatments." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folder");
        } catch (JSONException e) {}


        List<Treatment> treatments = autotuneIob.meals;
        // this sorts the treatments collection in order.
        //Collections.sort(treatments, (o1, o2) -> (int) (o2.date - o1.date));

        log.debug("Nb of meals: " + treatments.size() + " Nb of treatments: " + autotuneIob.getTreatmentsFromHistory().size() + " Nb of TempBasals: " + autotuneIob.getTemporaryBasalsFromHistory().size() + " Nb of ExtBol:" + autotuneIob.getExtendedBolusesFromHistory().size());
        Profile profileData = tunedprofile.profile;

        // Bloc between #21 and # 54 replaced by bloc below (just remove BG value below 39, Collections.sort probably not necessary because BG values already sorted...)
        List<BgReading> glucose=MainApp.getDbHelper().getBgreadingsDataFromTime(from,to, false);
        List<BgReading> glucoseData = new ArrayList<BgReading>();
        for (int i = 0; i < glucose.size(); i++) {
            if (glucose.get(i).value > 39) {
                glucoseData.add(glucose.get(i));
            }
        }
        Collections.sort(glucoseData, (o1, o2) -> (int) (o2.date - o1.date));

        // Bloc below replace bloc between #55 and #71
        // boluses and maxCarbs not used here ?,
        // IOBInputs are for iob calculation (done here in AutotuneIob Class)
        int boluses = 0;
        int maxCarbs = 0;
        if (treatments.size() < 1) {
            autotunePlugin.atLog("No treatments");
            return null;
        }
        List<BGDatum> csfGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> isfGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> uamGlucoseData = new ArrayList<BGDatum>();
        List<CRDatum> crData = new ArrayList<CRDatum>();

        //Bloc below replace bloc between #72 and #93
        // I keep it because BG lines in log are consistent between AAPS and Oref0
        List<BGDatum> bucketedData = new ArrayList<BGDatum>();
        bucketedData.add(new BGDatum(glucoseData.get(0)));
        //int j=0;
        int k=0; // index of first value used by bucket
        //for loop to validate and bucket the data
        for (int i=1; i < glucoseData.size(); ++i) {
            long BGTime = glucoseData.get(i).date;
            long lastBGTime = glucoseData.get(k).date;
            long elapsedMinutes = (BGTime - lastBGTime) / (60 * 1000);
            if (Math.abs(elapsedMinutes) >= 2) {
                //j++; // move to next bucket
                k = i; // store index of first value used by bucket
                bucketedData.add(new BGDatum((glucoseData.get(i))));
            } else {
                // average all readings within time deadband
                BgReading average = glucoseData.get(k);
                for(int l = k+1; l < i+1; l++) { average.value += glucoseData.get(l).value; }
                average.value=average.value/(i-k+1);
                bucketedData.add(new BGDatum(average));
            }
        }

        // Here treatments contains only meals data
        // bloc between #94 and #114 remove meals before first BG value
        // just difference (I have to check with Scott Leibrand) what happens if we have meal between 4 AM and first BG value, left here and removed in oref0-autotune, but I never had this case for the moment...

        // Bloc below replace bloc between #115 and #122 (initialize data before main loop)
        // crInitialxx are declaration to be able to use these data in whole loop
        boolean calculatingCR = false;
        boolean absorbing = false;
        boolean uam = false; // unannounced meal
        double mealCOB = 0d;
        double mealCarbs = 0;
        double crCarbs = 0;
        String type = "";
        double crInitialIOB =0d;
        double crInitialBG =0d;
        long crInitialCarbTime =0L;

        //categorize.js#123 (Note: don't need fullHistory because data are managed in AutotuneIob Class)
        //Here is main loop between #125 and #366
        // It's necessary to check and check again lines below because results are not consistent with oref0-autotune categorize.js results
        // Todo: request help from Scott here because there are differences between iob for autotune and iob for loop... (oref0 doesn't take into account profileswitch so iob when no TBR are wrong for loop...)
        // main for loop
        for (int i = bucketedData.size() - 5; i > 0; --i) {
            BGDatum glucoseDatum = bucketedData.get(i);
            //log.debug(glucoseDatum);
            long BGTime = glucoseDatum.date;

            // As we're processing each data point, go through the treatment.carbs and see if any of them are older than
            // the current BG data point.  If so, add those carbs to COB.

            Treatment treatment = treatments.size() > 0 ? treatments.get(treatments.size()-1) : null;
            double myCarbs = 0;
            if (treatment != null) {
                if (treatment.date < BGTime) {
                    if (treatment.carbs >= 1) {
                        mealCOB += treatment.carbs;
                        mealCarbs += treatment.carbs;
                        myCarbs = treatment.carbs;
                    }
                    treatments.remove(treatments.size()-1);
                }
            }

            double bg = 0;
            double avgDelta = 0;

            // TODO: re-implement interpolation to avoid issues here with gaps
            // calculate avgDelta as last 4 datapoints to better catch more rises after COB hits zero

            if (bucketedData.get(i).value != 0 && bucketedData.get(i + 4).value != 0) {
                //log.debug(bucketedData[i]);
                bg = bucketedData.get(i).value;
                if (bg < 40 || bucketedData.get(i + 4).value < 40) {
                    //process.stderr.write("!");
                    continue;
                }
                avgDelta = (bg - bucketedData.get(i + 4).value) / 4;

            } else {
                log.error("Could not find glucose data");
            }

            avgDelta = Round.roundTo(avgDelta,0.01);
            glucoseDatum.AvgDelta = avgDelta;

            //sens = ISF
            double sens = profileData.getIsfMgdl(BGTime);
            //log.debug("ISF data = " + sens);

            // for IOB calculations, use the average of the last 4 hours' basals to help convergence;
            // this helps since the basal this hour could be different from previous, especially if with autotune they start to diverge.
            // use the pumpbasalprofile to properly calculate IOB during periods where no temp basal is set
            //Philoul oref0-autotune doesn't use profile switch information, I think current bg profile is better for iob calculation
            //This part of code moved to AutotuneIob in getAbsoluteIOBTempBasals function

            // this is the current autotuned basal, used for everything else besides IOB calculations
            double currentBasal = profileData.getBasal(BGTime);

            //log.debug(currentBasal,basal1hAgo,basal2hAgo,basal3hAgo,IOBInputs.profile.currentBasal);
            // basalBGI is BGI of basal insulin activity.

            double basalBGI = Round.roundTo((currentBasal * sens / 60 * 5),0.01); // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
            //console.log(JSON.stringify(IOBInputs.profile));
            // call iob since calculated elsewhere
//****************************************************************************************************************************************
            //var getIOB = require('../iob');
            //var iob = getIOB(IOBInputs)[0];
            IobTotal bolusIob = autotuneIob.getCalculationToTimeTreatments(BGTime).round();
            IobTotal basalIob = autotuneIob.getAbsoluteIOBTempBasals(BGTime).round();
            IobTotal iob = IobTotal.combine(bolusIob, basalIob).round();
            //log.debug("Bolus activity: " + bolusIob.activity + " Basal activity: " + basalIob.activity + " Total activity: " + iob.activity);
            //log.debug("treatmentsPlugin Iob Activity: " + iob.activity + " Iob Basal: " + iob.basaliob + " Iob: " + iob.iob + " netbasalins: " + iob.netbasalinsulin + " netinsulin: " + iob.netInsulin);

            // activity times ISF times 5 minutes is BGI
            double BGI = Round.roundTo((-iob.activity * sens * 5) , 0.01);
            // datum = one glucose data point (being prepped to store in output)
            glucoseDatum.BGI = BGI;
            // calculating deviation
            double deviation = avgDelta - BGI;

            // set positive deviations to zero if BG is below 80
            if (bg < 80 && deviation > 0) {
                deviation = 0;
            }

            // rounding and storing deviation
            deviation = Round.roundTo(deviation, 0.01);
            glucoseDatum.deviation = deviation;

            
            // Then, calculate carb absorption for that 5m interval using the deviation.
            if (mealCOB > 0) {
                Profile profile;
                if (profileFunction.getProfile(BGTime) == null) {
                    log.debug("No profile selected");
                    return null;
                }
                profile = profileFunction.getProfile(BGTime);
                double ci = Math.max(deviation, sp.getDouble("openapsama_min_5m_carbimpact", 3.0));
                double absorbed = ci * profile.getIc() / sens;
                // Store the COB, and use it as the starting point for the next data point.
                mealCOB = Math.max(0, mealCOB - absorbed);
            }


            // Calculate carb ratio (CR) independently of CSF and ISF
            // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
            // For now, if another meal IOB/COB stacks on top of it, consider them together
            // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
            // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.
            if (mealCOB > 0 || calculatingCR) {
                // set initial values when we first see COB
                crCarbs += myCarbs;
                if (calculatingCR == false) {
                    crInitialIOB = iob.iob;
                    crInitialBG = glucoseDatum.value;
                    crInitialCarbTime = glucoseDatum.date;
                    autotunePlugin.atLog("CRInitialIOB: " + crInitialIOB + " CRInitialBG: " + crInitialBG + " CRInitialCarbTime: " + DateUtil.toISOString(crInitialCarbTime));
                }
                // keep calculatingCR as long as we have COB or enough IOB
                if (mealCOB > 0 && i > 1) {
                    calculatingCR = true;
                } else if (iob.iob > currentBasal / 2 && i > 1) {
                    calculatingCR = true;
                    // when COB=0 and IOB drops low enough, record end values and be done calculatingCR
                } else {
                    double crEndIOB = iob.iob;
                    double crEndBG = glucoseDatum.value;
                    long crEndTime = glucoseDatum.date;
                    log.debug("CREndIOB: " + crEndIOB + " CREndBG: " + crEndBG + " CREndTime: " + crEndTime);

                    CRDatum crDatum = new CRDatum();
                    crDatum.crInitialBG = crInitialBG;
                    crDatum.crInitialIOB = crInitialIOB;
                    crDatum.crInitialCarbTime = crInitialCarbTime;
                    crDatum.crEndBG = crEndBG;
                    crDatum.crEndIOB = crEndIOB;
                    crDatum.crEndTime = crEndTime;
                    //log.debug(CRDatum);
                    //String crDataString = "{\"CRInitialIOB\": " + CRInitialIOB + ",   \"CRInitialBG\": " + CRInitialBG + ",   \"CRInitialCarbTime\": " + CRInitialCarbTime + ",   \"CREndIOB\": " + CREndIOB + ",   \"CREndBG\": " + CREndBG + ",   \"CREndTime\": " + CREndTime + ",   \"CRCarbs\": " + CRCarbs + "}";
                    log.debug("CRDatum is: " + crDatum.toString() );

                    int CRElapsedMinutes = Math.round((crEndTime - crInitialCarbTime) / (1000 * 60));

                    //log.debug(CREndTime - CRInitialCarbTime, CRElapsedMinutes);
                    if (CRElapsedMinutes < 60 || (i == 1 && mealCOB > 0)) {
                        log.debug("Ignoring " + CRElapsedMinutes + " m CR period.");
                    } else {
                        crData.add(crDatum);
                    }

                    crCarbs = 0;
                    calculatingCR = false;
                }
            }

            // If mealCOB is zero but all deviations since hitting COB=0 are positive, assign those data points to CSFGlucoseData
            // Once deviations go negative for at least one data point after COB=0, we can use the rest of the data to tune ISF or basals
            if (mealCOB > 0 || !absorbing || mealCarbs > 0) {
                // if meal IOB has decayed, then end absorption after this data point unless COB > 0
                if (iob.iob < currentBasal / 2) {
                    absorbing = false;
                    // otherwise, as long as deviations are positive, keep tracking carb deviations
                } else if (deviation > 0) {
                    absorbing = true;
                } else {
                    absorbing = false;
                }
                if (!absorbing && mealCOB != 0) {
                    mealCarbs = 0;
                }
                // check previous "type" value, and if it wasn't csf, set a mealAbsorption start flag
                //log.debug(type);
                if (type.equals("csf") == false) {
                    glucoseDatum.mealAbsorption = "start";
                    autotunePlugin.atLog(glucoseDatum.mealAbsorption + " carb absorption");
                }
                type = "csf";
                glucoseDatum.mealCarbs = (int) mealCarbs;
                //if (i == 0) { glucoseDatum.mealAbsorption = "end"; }
                csfGlucoseData.add(glucoseDatum);
            } else {
                // check previous "type" value, and if it was csf, set a mealAbsorption end flag
                if (type == "csf") {
                    csfGlucoseData.get(csfGlucoseData.size() - 1).mealAbsorption = "end";
                    log.debug(csfGlucoseData.get(csfGlucoseData.size() - 1).mealAbsorption + " carb absorption");
                }

                if ((iob.iob > currentBasal || deviation > 6 || uam )) {
                    if (deviation > 0) {
                        uam = true;
                    } else {
                        uam = false;
                    }
                    if (type != "uam") {
                        glucoseDatum.uamAbsorption = "start";
                        autotunePlugin.atLog(glucoseDatum.uamAbsorption + " unannnounced meal absorption");
                    }
                    type = "uam";
                    uamGlucoseData.add(glucoseDatum);
                } else {
                    if (type == "uam") {
                        autotunePlugin.atLog("end unannounced meal absorption");
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
                        type = "basal";
                        basalGlucoseData.add(glucoseDatum);
                    } else {
                        if (avgDelta > 0 && avgDelta > -2 * BGI) {
                            //type="unknown"
                            type = "basal";
                            basalGlucoseData.add(glucoseDatum);
                        } else {
                            type = "ISF";
                            isfGlucoseData.add(glucoseDatum);
                        }
                    }
                }
            }
            // debug line to print out all the things
//            BGDateArray = BGDate.toString().split(" ");
//            BGTime = BGDateArray[4];
            autotunePlugin.atLog((absorbing?1:0)+" mealCOB: "+Math.round(mealCOB)+" mealCarbs: "+Math.round(mealCarbs)+" basalBGI: "+Round.roundTo(basalBGI,0.1)+" BGI: "+BGI+" IOB: "+iob.iob+" at "+new Date(BGTime).toString()+" dev: "+deviation+" avgDelta: "+avgDelta +" "+ type);
        }

        log.debug("end of loop bucket");
//        iobInputs.profile = new TunedProfile(opts.profile);
//        iobInputs.history = opts.pumpHistory;
//        iobInputs.treatments = opts.treatments;
        //treatments = autotuneIob.find_insulin(iobInputs);
//****************************************************************************************************************************************
        /* Code template for IOB calculation trom tempBasal Object
        IobTotal iob = new IobTotal(now);
        Profile profile = ProfileFunctions.getInstance().getProfile(now);
        if (profile != null)
            iob = tempBasal.iobCalc(now, profile);
         */
        treatments = autotuneIob.getTreatmentsFromHistory();

// categorize.js Lines 372-383
        for (CRDatum crDatum : crData) {
            crDatum.crInsulin = dosed(crDatum.crInitialCarbTime,crDatum.crInitialCarbTime,treatments);
        }
// categorize.js Lines 384-436
        int CSFLength = csfGlucoseData.size();
        int ISFLength = isfGlucoseData.size();
        int UAMLength = uamGlucoseData.size();
        int basalLength = basalGlucoseData.size();

        if (sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false)) {
            log.debug("Categorizing all UAM data as basal.");
            basalGlucoseData.addAll(uamGlucoseData);
        } else if (CSFLength > 12) {
            log.debug("Found at least 1h of carb absorption: assuming all meals were announced, and categorizing UAM data as basal.");
            basalGlucoseData.addAll(uamGlucoseData);
        } else {
            if (2*basalLength < UAMLength) {
                //log.debug(basalGlucoseData, UAMGlucoseData);
                log.debug("Warning: too many deviations categorized as UnAnnounced Meals");
                log.debug("Adding", UAMLength, "UAM deviations to", basalLength, "basal ones");
                basalGlucoseData.addAll(uamGlucoseData);
                //log.debug(basalGlucoseData);
                // if too much data is excluded as UAM, add in the UAM deviations, but then discard the highest 50%
                Collections.sort(basalGlucoseData, (o1, o2) -> (int) (o1.deviation - o2.deviation));
                List<BGDatum> newBasalGlucose = new ArrayList<BGDatum>();

                for (int i = 0; i < basalGlucoseData.size() / 2; i++) {
                    newBasalGlucose.add(basalGlucoseData.get(i));
                }
                //log.debug(newBasalGlucose);
                basalGlucoseData = newBasalGlucose;
                log.debug("and selecting the lowest 50%, leaving" + basalGlucoseData.size() + "basal+UAM ones");
            }

            if (2*ISFLength < UAMLength) {
                log.debug("Adding " + UAMLength + " UAM deviations to " + ISFLength + " ISF ones");
                isfGlucoseData.addAll(uamGlucoseData);
                // if too much data is excluded as UAM, add in the UAM deviations to ISF, but then discard the highest 50%
                Collections.sort(isfGlucoseData, (o1, o2) -> (int) (o1.deviation - o2.deviation));
                List<BGDatum> newISFGlucose = new ArrayList<BGDatum>();
                for (int i = 0; i < isfGlucoseData.size() / 2; i++) {
                    newISFGlucose.add(isfGlucoseData.get(i));
                }
                //console.error(newISFGlucose);
                isfGlucoseData = newISFGlucose;
                log.error("and selecting the lowest 50%, leaving" + isfGlucoseData.size() + "ISF+UAM ones");
                //log.error(ISFGlucoseData.length, UAMLength);
            }
        }
        basalLength = basalGlucoseData.size();
        ISFLength = isfGlucoseData.size();
        if ( 4*basalLength + ISFLength < CSFLength && ISFLength < 10 ) {
            log.debug("Warning: too many deviations categorized as meals");
            //log.debug("Adding",CSFLength,"CSF deviations to",basalLength,"basal ones");
            //var basalGlucoseData = basalGlucoseData.concat(CSFGlucoseData);
            log.debug("Adding",CSFLength,"CSF deviations to",ISFLength,"ISF ones");
            isfGlucoseData.addAll(csfGlucoseData);
            csfGlucoseData = new ArrayList<>();
        }

// categorize.js Lines 437-444
        log.debug("CRData: "+crData.size());
        log.debug("CSFGlucoseData: "+ csfGlucoseData.size());
        log.debug("ISFGlucoseData: "+ isfGlucoseData.size());
        log.debug("BasalGlucoseData: "+basalGlucoseData.size());
// Here is the end of categorize.js file

/* bloc below is for --tune-insulin-curve not developed for the moment
// these lines are in index.js file (autotune-prep folder)
        if (inputs.tune_insulin_curve) {
            if (opts.profile.curve === 'bilinear') {
                console.error('--tune-insulin-curve is set but only valid for exponential curves');
            } else {
                var minDeviations = 1000000;
                var newDIA = 0;
                var diaDeviations = [];
                var peakDeviations = [];
                var currentDIA = opts.profile.dia;
                var currentPeak = opts.profile.insulinPeakTime;

                var consoleError = console.error;
                console.error = function() {};

                var startDIA=currentDIA - 2;
                var endDIA=currentDIA + 2;
                for (var dia=startDIA; dia <= endDIA; ++dia) {
                    var sqrtDeviations = 0;
                    var deviations = 0;
                    var deviationsSq = 0;

                    opts.profile.dia = dia;

                    var curve_output = categorize(opts);
                    var basalGlucose = curve_output.basalGlucoseData;

                    for (var hour=0; hour < 24; ++hour) {
                        for (var i=0; i < basalGlucose.length; ++i) {
                            var BGTime;

                            if (basalGlucose[i].date) {
                                BGTime = new Date(basalGlucose[i].date);
                            } else if (basalGlucose[i].displayTime) {
                                BGTime = new Date(basalGlucose[i].displayTime.replace('T', ' '));
                            } else if (basalGlucose[i].dateString) {
                                BGTime = new Date(basalGlucose[i].dateString);
                            } else {
                                consoleError("Could not determine last BG time");
                            }

                            var myHour = BGTime.getHours();
                            if (hour === myHour) {
                                //console.error(basalGlucose[i].deviation);
                                sqrtDeviations += Math.pow(parseFloat(Math.abs(basalGlucose[i].deviation)), 0.5);
                                deviations += Math.abs(parseFloat(basalGlucose[i].deviation));
                                deviationsSq += Math.pow(parseFloat(basalGlucose[i].deviation), 2);
                            }
                        }
                    }

                    var meanDeviation = Math.round(Math.abs(deviations/basalGlucose.length)*1000)/1000;
                    var SMRDeviation = Math.round(Math.pow(sqrtDeviations/basalGlucose.length,2)*1000)/1000;
                    var RMSDeviation = Math.round(Math.pow(deviationsSq/basalGlucose.length,0.5)*1000)/1000;
                    consoleError('insulinEndTime', dia, 'meanDeviation:', meanDeviation, 'SMRDeviation:', SMRDeviation, 'RMSDeviation:',RMSDeviation, '(mg/dL)');
                    diaDeviations.push({
                            dia: dia,
                            meanDeviation: meanDeviation,
                            SMRDeviation: SMRDeviation,
                            RMSDeviation: RMSDeviation,
        });
                    autotune_prep_output.diaDeviations = diaDeviations;

                    deviations = Math.round(deviations*1000)/1000;
                    if (deviations < minDeviations) {
                        minDeviations = Math.round(deviations*1000)/1000;
                        newDIA = dia;
                    }
                }

                // consoleError('Optimum insulinEndTime', newDIA, 'mean deviation:', Math.round(minDeviations/basalGlucose.length*1000)/1000, '(mg/dL)');
                //consoleError(diaDeviations);

                minDeviations = 1000000;

                var newPeak = 0;
                opts.profile.dia = currentDIA;
                //consoleError(opts.profile.useCustomPeakTime, opts.profile.insulinPeakTime);
                if ( ! opts.profile.useCustomPeakTime === true && opts.profile.curve === "ultra-rapid" ) {
                    opts.profile.insulinPeakTime = 55;
                } else if ( ! opts.profile.useCustomPeakTime === true ) {
                    opts.profile.insulinPeakTime = 75;
                }
                opts.profile.useCustomPeakTime = true;

                var startPeak=opts.profile.insulinPeakTime - 10;
                var endPeak=opts.profile.insulinPeakTime + 10;
                for (var peak=startPeak; peak <= endPeak; peak=(peak+5)) {
                    sqrtDeviations = 0;
                    deviations = 0;
                    deviationsSq = 0;

                    opts.profile.insulinPeakTime = peak;


                    curve_output = categorize(opts);
                    basalGlucose = curve_output.basalGlucoseData;

                    for (hour=0; hour < 24; ++hour) {
                        for (i=0; i < basalGlucose.length; ++i) {
                            if (basalGlucose[i].date) {
                                BGTime = new Date(basalGlucose[i].date);
                            } else if (basalGlucose[i].displayTime) {
                                BGTime = new Date(basalGlucose[i].displayTime.replace('T', ' '));
                            } else if (basalGlucose[i].dateString) {
                                BGTime = new Date(basalGlucose[i].dateString);
                            } else {
                                consoleError("Could not determine last BG time");
                            }

                            myHour = BGTime.getHours();
                            if (hour === myHour) {
                                //console.error(basalGlucose[i].deviation);
                                sqrtDeviations += Math.pow(parseFloat(Math.abs(basalGlucose[i].deviation)), 0.5);
                                deviations += Math.abs(parseFloat(basalGlucose[i].deviation));
                                deviationsSq += Math.pow(parseFloat(basalGlucose[i].deviation), 2);
                            }
                        }
                    }
                    console.error(deviationsSq);

                    meanDeviation = Math.round(deviations/basalGlucose.length*1000)/1000;
                    SMRDeviation = Math.round(Math.pow(sqrtDeviations/basalGlucose.length,2)*1000)/1000;
                    RMSDeviation = Math.round(Math.pow(deviationsSq/basalGlucose.length,0.5)*1000)/1000;
                    consoleError('insulinPeakTime', peak, 'meanDeviation:', meanDeviation, 'SMRDeviation:', SMRDeviation, 'RMSDeviation:',RMSDeviation, '(mg/dL)');
                    peakDeviations.push({
                            peak: peak,
                            meanDeviation: meanDeviation,
                            SMRDeviation: SMRDeviation,
                            RMSDeviation: RMSDeviation,
        });
                    autotune_prep_output.diaDeviations = diaDeviations;

                    deviations = Math.round(deviations*1000)/1000;
                    if (deviations < minDeviations) {
                        minDeviations = Math.round(deviations*1000)/1000;
                        newPeak = peak;
                    }
                }

                //consoleError('Optimum insulinPeakTime', newPeak, 'mean deviation:', Math.round(minDeviations/basalGlucose.length*1000)/1000, '(mg/dL)');
                //consoleError(peakDeviations);
                autotune_prep_output.peakDeviations = peakDeviations;

                console.error = consoleError;
            }
        }
        */

        return new PreppedGlucose(crData, csfGlucoseData, isfGlucoseData, basalGlucoseData);

        // and may be later
        // return new PreppedGlucose(crData, csfGlucoseData, isfGlucoseData, basalGlucoseData, diaDeviations, peakDeviations);
    }

    //dosed.js full
    private static double dosed(long start, long end, List<Treatment> treatments) {
        double insulinDosed = 0;
        if (treatments.size()==0) {
            log.debug("No treatments to process.");
            return 0;
        }

        for (Treatment treatment:treatments ) {
            if(treatment.insulin != 0 && treatment.date > start && treatment.date <= end) {
                insulinDosed += treatment.insulin;
            }
        }
        log.debug("insulin dosed: " + insulinDosed);

        return Round.roundTo(insulinDosed,0.001);
    }


}

