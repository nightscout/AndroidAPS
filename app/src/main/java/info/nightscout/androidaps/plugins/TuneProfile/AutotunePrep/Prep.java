package info.nightscout.androidaps.plugins.TuneProfile.AutotunePrep;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.TuneProfile.BGDatum;
import info.nightscout.androidaps.plugins.TuneProfile.CRDatum;
import info.nightscout.androidaps.plugins.TuneProfile.NSService;
import info.nightscout.androidaps.plugins.TuneProfile.Opts;
import info.nightscout.androidaps.plugins.TuneProfile.PrepOutput;
import info.nightscout.androidaps.plugins.TuneProfile.TuneProfilePlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.Round;





public class Prep {
    private boolean useNSData = false;
    public boolean nsDataDownloaded = false;
    private static Logger log = LoggerFactory.getLogger(TuneProfilePlugin.class);

    private NSService nsService = new NSService();

    public PrepOutput categorizeBGDatums(Opts opts) throws JSONException, ParseException, IOException {
        List<Treatment> treatments = opts.treatments;
        // this sorts the treatments collection in order.
        Collections.sort(treatments, (o1, o2) -> (int) (o2.getDate() - o1.getDate()));

        Profile profileData = opts.profile;

        List<BgReading> glucoseData = new ArrayList<BgReading>(); // opts.glucose;    //Todo: Philoul add glucose inputs in opts

        for (int i = 0; i < opts.glucose.size(); i++) {
            if (opts.glucose.get(i).value > 39) {
                glucoseData.add(opts.glucose.get(i));
            }
        }
        Collections.sort(glucoseData, (o1, o2) -> (int) (o2.date - o1.date));

 /*       glucoseData = opts.glucose.map(function prepGlucose (obj) {
            //Support the NS sgv field to avoid having to convert in a custom way
            obj.glucose = obj.glucose || obj.sgv;

            if (obj.date) {
                //obj.BGTime = new Date(obj.date);
            } else if (obj.displayTime) {
                // Attempt to get date from displayTime
                obj.date = new Date(obj.displayTime.replace('T', ' ')).getTime();
            } else if (obj.dateString) {
                // Attempt to get date from dateString
                obj.date = new Date(obj.dateString).getTime();
            }// else { console.error("Could not determine BG time"); }

            if (!obj.dateString)
            {
                obj.dateString = new Date(tz(obj.date)).toISOString();
            }
            return obj;
        }).filter(function filterRecords(obj) {
            // Only take records with a valid date record
            // and a glucose value, which is also above 39
            return (obj.date && obj.glucose && obj.glucose >=39);
        }).sort(function (a, b) {
            // sort the collection in order
            return b.date - a.date;
        });
    }

  */

        log.debug("NSService should receive " + new Date(opts.start) + " to " + new Date(opts.end));

        int boluses = 0;
        int maxCarbs = 0;


        //var IOBInputs = {
        //        profile: profileData,
        //        history: opts.pumpHistory
        //};

        List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> UAMGlucoseData = new ArrayList<BGDatum>();
        List<CRDatum> CRData = new ArrayList<CRDatum>();

        log.debug("Treatmets size: " + treatments.size());
        //trim treatments size
        for (int i = 0; i < treatments.size(); i++) {
            if (treatments.get(i).date < opts.start || treatments.get(i).date > opts.end) {
                treatments.remove(i);
            }
        }
        /*} catch (IOException e) {
            e.printStackTrace();
        }*/
        if (treatments.size() < 1) {
            log.debug("No treatments");
            return null;
        }


        JSONObject IOBInputs = new JSONObject();
        IOBInputs.put("profile", opts.profile);
        IOBInputs.put("history", "pumpHistory");


        List<BGDatum> bucketedData = new ArrayList<BGDatum>();

        double CRInitialIOB = 0d;
        double CRInitialBG = 0d;
        Date CRInitialCarbTime = null;

        // BasalGlucosedata is null
//        bucketedData.add(basalGlucoseData.get(0));
        int j = 0;
        //for loop to validate and bucket the data
        for (int i = 1; i < glucoseData.size(); ++i) {
            long BGTime = 0;
            long lastBGTime = 0;
            if (glucoseData.get(i).date != 0) {
                BGTime = new Date(glucoseData.get(i).date).getTime();
            } else {
                log.error("Could not determine last BG time");
            }
            if (i > 1) {
                if (glucoseData.get(i).value < 39 || glucoseData.get(i - 1).value < 39) {
                    continue;
                }
            } else if (glucoseData.get(i).value < 39) {
                continue;
            }
            long elapsedMinutes = (BGTime - lastBGTime) / (60 * 1000);
            if (Math.abs(elapsedMinutes) > 2) {
                j++;
                if (bucketedData.size() < j)
                    bucketedData.add(new BGDatum(glucoseData.get(i)));
                else
                    bucketedData.set(j, new BGDatum(glucoseData.get(i)));
//                bucketedData<j>.date = BGTime;
                /*if (! bucketedData[j].dateString) {
                    bucketedData[j].dateString = BGTime.toISOString();
                }*/
            } else {
                // if duplicate, average the two
                BgReading average = new BgReading();
                average.copyFrom(bucketedData.get(j));
                average.value = (bucketedData.get(j).value + glucoseData.get(i).value) / 2;
                bucketedData.set(j, new BGDatum(average));
            }
        }
        // go through the treatments and remove any that are older than the oldest glucose value
        //log.debug(treatments);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss''");
        log.debug("Treatments size before clear: " + treatments.size());
        log.debug("Treatment(0) " + new Date(treatments.get(0).date).toString() + " last " + new Date(treatments.get(treatments.size() - 1).date).toString());
        for (int i = treatments.size() - 1; i > 0; --i) {
            Treatment treatment = treatments.get(i);
            //log.debug(treatment);
            if (treatment != null) {
                Date treatmentDate = new Date(treatment.date);
                long treatmentTime = treatmentDate.getTime();
                BgReading glucoseDatum = bucketedData.get(bucketedData.size() - 1);
                //log.debug(glucoseDatum);
                Date BGDate = new Date(glucoseDatum.date);
                long BGTime = BGDate.getTime();

//                log.debug("Oldest BG data is: "+format.format(BGDate)+" oldest treatment is: "+format.format(new Date(treatments.get(treatments.size()-1).date).toGMTString()));
                if (treatmentTime < BGTime) {
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
//categorize.js#125
        for (int i = bucketedData.size() - 5; i > 0; --i) {
            BGDatum glucoseDatum = bucketedData.get(i);
            //log.debug(glucoseDatum);
            Date BGDate = new Date(glucoseDatum.date);
            long BGTime = BGDate.getTime();

            // As we're processing each data point, go through the treatment.carbs and see if any of them are older than
            // the current BG data point.  If so, add those carbs to COB.
            Treatment treatment = treatments.get(treatments.size()-1);
            int myCarbs = 0;
//Todo: Philoul here there is a loop that doesn't exist in original categorize#134
//            for (int jj = 0; jj < treatments.size() - 1; jj++) {
//                Treatment treatment = treatments.get(jj);
            if (treatment != null) {
                Date treatmentDate = new Date(treatment.date);
                long treatmentTime = treatmentDate.getTime();
                //log.debug(treatmentDate);

                if (treatmentTime < BGTime) {
                    if (treatment.carbs >= 1) {
                        // Here I parse Integers not float like the original source categorize.js#136

                        mealCOB += (int) (treatment.carbs);
                        mealCarbs += (int) (treatment.carbs);
                        myCarbs = (int) treatment.carbs;

                    }
                    treatments.remove(treatments.size()-1);
                }
            }
//            }
            double BG = 0;
            double avgDelta = 0;

            // TODO: re-implement interpolation to avoid issues here with gaps
            // calculate avgDelta as last 4 datapoints to better catch more rises after COB hits zero

            if (bucketedData.get(i).value != 0 && bucketedData.get(i + 4).value != 0) {
                //log.debug(bucketedData[i]);
                BG = bucketedData.get(i).value;
                if (BG < 40 || bucketedData.get(i + 4).value < 40) {
                    //process.stderr.write("!");
                    continue;
                }
                avgDelta = (BG - bucketedData.get(i + 4).value) / 4;

            } else {
                log.error("Could not find glucose data");
            }


            avgDelta = avgDelta * 100 / 100;
            glucoseDatum.AvgDelta = avgDelta;

            //sens = ISF
//            int sens = ISF.isfLookup(IOBInputs.profile.isfProfile,BGDate);
            double sens = opts.profile.getIsfMgdl(BGTime);
//            IOBInputs.clock=BGDate.toString();
            // trim down IOBInputs.history to just the data for 6h prior to BGDate
            //log.debug(IOBInputs.history[0].created_at);
            List<Treatment> newHistory = null;
            for (int h = 0; h < fullHistory.size(); h++) {
//                Date hDate = new Date(fullHistory.get(h).created_at) TODO: When we get directly from Ns there should be created_at
                Date hDate = new Date(fullHistory.get(h).date);
                //log.debug(fullHistory[i].created_at, hDate, BGDate, BGDate-hDate);
                //if (h == 0 || h == fullHistory.length - 1) {
                //log.debug(hDate, BGDate, hDate-BGDate)
                //}
                if (BGDate.getTime() - hDate.getTime() < 6 * 60 * 60 * 1000 && BGDate.getTime() - hDate.getTime() > 0) {
                    //process.stderr.write("i");
                    //log.debug(hDate);
                    newHistory.add(fullHistory.get(h));
                }
            }
            if (newHistory != null)
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
            if (hourOfDay > 3) {
                basal1hAgo = TuneProfilePlugin.getBasal(hourOfDay - 1);
                basal2hAgo = TuneProfilePlugin.getBasal(hourOfDay - 2);
                basal3hAgo = TuneProfilePlugin.getBasal(hourOfDay - 3);
            } else {
                if (hourOfDay - 1 < 0)
                    basal1hAgo = TuneProfilePlugin.getBasal(24 - 1);
                else
                    basal1hAgo = TuneProfilePlugin.getBasal(hourOfDay - 1);
                if (hourOfDay - 2 < 0)
                    basal2hAgo = TuneProfilePlugin.getBasal(24 - 2);
                else
                    basal2hAgo = TuneProfilePlugin.getBasal(hourOfDay - 2);
                if (hourOfDay - 3 < 0)
                    basal3hAgo = TuneProfilePlugin.getBasal(24 - 3);
                else
                    basal3hAgo = TuneProfilePlugin.getBasal(hourOfDay - 3);
            }
            double sum = currentPumpBasal + basal1hAgo + basal2hAgo + basal3hAgo;
//            IOBInputs.profile.currentBasal = Math.round((sum/4)*1000)/1000;

            // this is the current autotuned basal, used for everything else besides IOB calculations
            double currentBasal = TuneProfilePlugin.getBasal(hourOfDay);

            //log.debug(currentBasal,basal1hAgo,basal2hAgo,basal3hAgo,IOBInputs.profile.currentBasal);
            // basalBGI is BGI of basal insulin activity.
            double basalBGI = Math.round((currentBasal * sens / 60 * 5) * 100) / 100; // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
            //console.log(JSON.stringify(IOBInputs.profile));
            // call iob since calculated elsewhere
            IobTotal iob = TuneProfilePlugin.calculateFromTreatmentsAndTemps(BGDate.getTime());
            //log.debug(JSON.stringify(iob));

            // activity times ISF times 5 minutes is BGI
            double BGI = Math.round((-iob.activity * sens * 5) * 100) / 100;
            // datum = one glucose data point (being prepped to store in output)
            glucoseDatum.BGI = BGI;
            // calculating deviation
            double deviation = avgDelta - BGI;

            // set positive deviations to zero if BG is below 80
            if (BG < 80 && deviation > 0) {
                deviation = 0;
            }

            // rounding and storing deviation
            deviation = TuneProfilePlugin.round(deviation, 2);
            glucoseDatum.deviation = deviation;


            // Then, calculate carb absorption for that 5m interval using the deviation.
            if (mealCOB > 0) {
                Profile profile;
                if (ProfileFunctions.getInstance().getProfile() == null) {
                    log.debug("No profile selected");
                    return null;
                }
                profile = ProfileFunctions.getInstance().getProfile();
                double ci = Math.max(deviation, SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
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
                CRCarbs += myCarbs;
                if (calculatingCR == false) {
                    CRInitialIOB = iob.iob;
                    CRInitialBG = glucoseDatum.value;
                    CRInitialCarbTime = new Date(glucoseDatum.date);
                    log.debug("CRInitialIOB: " + CRInitialIOB + " CRInitialBG: " + CRInitialBG + " CRInitialCarbTime: " + CRInitialCarbTime.toString());
                }
                // keep calculatingCR as long as we have COB or enough IOB
                if (mealCOB > 0 && i > 1) {
                    calculatingCR = true;
                } else if (iob.iob > currentBasal / 2 && i > 1) {
                    calculatingCR = true;
                    // when COB=0 and IOB drops low enough, record end values and be done calculatingCR
                } else {
                    double CREndIOB = iob.iob;
                    double CREndBG = glucoseDatum.value;
                    Date CREndTime = new Date(glucoseDatum.date);
                    log.debug("CREndIOB: " + CREndIOB + " CREndBG: " + CREndBG + " CREndTime: " + CREndTime);
                    //TODO:Fix this one as it produces error
//                    JSONObject CRDatum = new JSONObject("{\"CRInitialIOB\": "+CRInitialIOB+",   \"CRInitialBG\": "+CRInitialBG+",   \"CRInitialCarbTime\": "+CRInitialCarbTime+",   \"CREndIOB\": " +CREndIOB+",   \"CREndBG\": "+CREndBG+",   \"CREndTime\": "+CREndTime+                            ",   \"CRCarbs\": "+CRCarbs+"}");
                    String crDataString = "{\"CRInitialIOB\": " + CRInitialIOB + ",   \"CRInitialBG\": " + CRInitialBG + ",   \"CRInitialCarbTime\": " + CRInitialCarbTime.getTime() + ",   \"CREndIOB\": " + CREndIOB + ",   \"CREndBG\": " + CREndBG + ",   \"CREndTime\": " + CREndTime.getTime() + ",   \"CRCarbs\": " + CRCarbs + "}";
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
                    if (CRElapsedMinutes < 60 || (i == 1 && mealCOB > 0)) {
                        log.debug("Ignoring " + CRElapsedMinutes + " m CR period.");
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
                if (iob.iob < currentBasal / 2) {
                    absorbing = 0;
                    // otherwise, as long as deviations are positive, keep tracking carb deviations
                } else if (deviation > 0) {
                    absorbing = 1;
                } else {
                    absorbing = 0;
                }
                if (absorbing != 0 && mealCOB != 0) {
                    mealCarbs = 0;
                }
                // check previous "type" value, and if it wasn't csf, set a mealAbsorption start flag
                //log.debug(type);
                if (type.equals("csf") == false) {
                    glucoseDatum.mealAbsorption = "start";
                    log.debug(glucoseDatum.mealAbsorption + " carb absorption");
                }
                type = "csf";
                glucoseDatum.mealCarbs = mealCarbs;
                //if (i == 0) { glucoseDatum.mealAbsorption = "end"; }
                CSFGlucoseData.add(glucoseDatum);
            } else {
                // check previous "type" value, and if it was csf, set a mealAbsorption end flag
                if (type == "csf") {
                    CSFGlucoseData.get(CSFGlucoseData.size() - 1).mealAbsorption = "end";
                    log.debug(CSFGlucoseData.get(CSFGlucoseData.size() - 1).mealAbsorption + " carb absorption");
                }

                if ((iob.iob > currentBasal || deviation > 6 || uam > 0)) {
                    if (deviation > 0) {
                        uam = 1;
                    } else {
                        uam = 0;
                    }
                    if (type != "uam") {
                        glucoseDatum.uamAbsorption = "start";
                        log.debug(glucoseDatum.uamAbsorption + " unannnounced meal absorption");
                    }
                    type = "uam";
                    UAMGlucoseData.add(glucoseDatum);
                } else {
                    if (type == "uam") {
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
                        type = "basal";
                        basalGlucoseData.add(glucoseDatum);
                    } else {
                        if (avgDelta > 0 && avgDelta > -2 * BGI) {
                            //type="unknown"
                            type = "basal";
                            basalGlucoseData.add(glucoseDatum);
                        } else {
                            type = "ISF";
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

//    IOBInputs = {
//        profile: profileData
//    ,   history: opts.pumpHistory
//    };
//	  treatments = find_insulin(IOBInputs);
        //Todo Philoul check if line below gets insulin from IOBInputs
        treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();
// categorize.js Lines 372-383
        for (CRDatum crDatum : CRData) {
            Opts dosedOpts = new Opts();
            dosedOpts.treatments = treatments;
            dosedOpts.start = crDatum.CRInitialCarbTime;
            dosedOpts.end = crDatum.CREndTime;
            crDatum.CRInsulin = dosed(dosedOpts);
        }

// categorize.js Lines 384-436
        int CSFLength = CSFGlucoseData.size();
        int ISFLength = ISFGlucoseData.size();
        int UAMLength = UAMGlucoseData.size();
        int basalLength = basalGlucoseData.size();

        if (opts.categorize_uam_as_basal) {
            log.debug("Categorizing all UAM data as basal.");
            basalGlucoseData.addAll(UAMGlucoseData);
        } else if (CSFLength > 12) {
            log.debug("Found at least 1h of carb absorption: assuming all meals were announced, and categorizing UAM data as basal.");
            basalGlucoseData.addAll(UAMGlucoseData);
        } else {
            if (2*basalLength < UAMLength) {
                //log.debug(basalGlucoseData, UAMGlucoseData);
                log.debug("Warning: too many deviations categorized as UnAnnounced Meals");
                log.debug("Adding", UAMLength, "UAM deviations to", basalLength, "basal ones");
                basalGlucoseData.addAll(UAMGlucoseData);
                //log.debug(basalGlucoseData);
                // if too much data is excluded as UAM, add in the UAM deviations, but then discard the highest 50%
                Collections.sort(basalGlucoseData, (o1, o2) -> (int) (o1.deviation - o2.deviation));
                List<BGDatum> newBasalGlucose = new ArrayList<BGDatum>();
                ;
                for (int i = 0; i < basalGlucoseData.size() / 2; i++) {
                    newBasalGlucose.add(basalGlucoseData.get(i));
                }
                //log.debug(newBasalGlucose);
                basalGlucoseData = newBasalGlucose;
                log.debug("and selecting the lowest 50%, leaving" + basalGlucoseData.size() + "basal+UAM ones");
            }

            if (2*ISFLength < UAMLength) {
                log.debug("Adding " + UAMLength + " UAM deviations to " + ISFLength + " ISF ones");
                ISFGlucoseData.addAll(UAMGlucoseData);
                // if too much data is excluded as UAM, add in the UAM deviations to ISF, but then discard the highest 50%
                Collections.sort(ISFGlucoseData, (o1, o2) -> (int) (o1.deviation - o2.deviation));
                List<BGDatum> newISFGlucose = new ArrayList<BGDatum>();
                for (int i = 0; i < ISFGlucoseData.size() / 2; i++) {
                    newISFGlucose.add(ISFGlucoseData.get(i));
                }
                //console.error(newISFGlucose);
                ISFGlucoseData = newISFGlucose;
                log.error("and selecting the lowest 50%, leaving" + ISFGlucoseData.size() + "ISF+UAM ones");
                //log.error(ISFGlucoseData.length, UAMLength);
            }
        }
        basalLength = basalGlucoseData.size();
        ISFLength = ISFGlucoseData.size();
        if ( 4*basalLength + ISFLength < CSFLength && ISFLength < 10 ) {
            log.debug("Warning: too many deviations categorized as meals");
            //log.debug("Adding",CSFLength,"CSF deviations to",basalLength,"basal ones");
            //var basalGlucoseData = basalGlucoseData.concat(CSFGlucoseData);
            log.debug("Adding",CSFLength,"CSF deviations to",ISFLength,"ISF ones");
            ISFGlucoseData.addAll(CSFGlucoseData);
            CSFGlucoseData = new ArrayList<>();
        }

// categorize.js Lines 437-444
        PrepOutput prepOutput = new PrepOutput();
        prepOutput.CRData=CRData;
        prepOutput.CSFGlucoseData=CSFGlucoseData;
        prepOutput.ISFGlucoseData=ISFGlucoseData;
        prepOutput.basalGlucoseData=basalGlucoseData;

        log.debug("CRData: "+CRData.size());
        log.debug("CSFGlucoseData: "+CSFGlucoseData.size());
        log.debug("ISFGlucoseData: "+ISFGlucoseData.size());
        log.debug("BasalGlucoseData: "+basalGlucoseData.size());
//        String returnJSON = "{\"CRData\":"+CRData.toString()+",\"CSFGlucoseData\": "+CSFGlucoseData.toString()+",\"ISFGlucoseData\": "+ISFGlucoseData.toString()+",\"basalGlucoseData\": "+basalGlucoseData.toString()+"}";
//        log.debug("Returning: "+returnJSON);
        return prepOutput;
    }

    //dosed.js full
    private double dosed(Opts opts) {
        long start = opts.start;
        long end = opts.end;
        List<Treatment> treatments = opts.treatments;
        Profile profile_data = opts.profile;
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


    // index.js // opts = inputs
    public PrepOutput generate (Opts opts) throws JSONException, ParseException, IOException {

        PrepOutput autotune_prep_output = categorizeBGDatums(opts);

        if (opts.tune_insulin_curve) {
/* Todo if necessary if tune insulin curve is set

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

 */
        }


        return autotune_prep_output;
    }

}

