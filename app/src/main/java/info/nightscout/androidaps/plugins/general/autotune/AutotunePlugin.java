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
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.TimeZone;
import java.util.ArrayList;
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

 * Update by philoul on 05/2020 (complete refactor of AutotunePlugin)
 *  TODO: add Preference for main settings (may be advanced settings for % of adjustment (default 20%))
 *  Manage ProfileSwitch once Results validated (Still lots of work!)
 *
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
//    private AutotuneFragment autotuneFragment;
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
//            AutotuneFragment autotuneFragment,
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
//        this.autotuneFragment = autotuneFragment;
        this.aapsLogger = aapsLogger;
    }

//    @Override
    public String getFragmentClass() {
        return AutotuneFragment.class.getName();
    }

    public void invoke(String initiator, boolean allowNotification) {
        // invoke
    }

    //Todo add profile selector in AutotuneFragment to allow running autotune plugin with other profiles than current
    public String aapsAutotune(int daysBack) {
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

        profile = profileFunction.getProfile(now);
        //try {
        //    AutotuneFS.createAutotunefile("aapsfullprofile.json", profile.getData().toString(2), true);
        //} catch (JSONException e) {}
        ATProfile tunedProfile = new ATProfile(profile);
        tunedProfile.profilename=resourceHelper.gs(R.string.autotune_tunedprofile_name);
        ATProfile pumpprofile = new ATProfile(profile);
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
                    AutotuneFS.createAutotunefile("aaps-entries." + AutotuneFS.formatDate(new Date(from)) + ".json", autotuneIob.glucosetoJSON().toString(2));
                    atLog("Create ns-entries." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folder");
                    //ns-treatments files are for result compare with oref0 autotune on virtual machine (include treatments ,tempBasal and extended
                    AutotuneFS.createAutotunefile("aaps-treatments." + AutotuneFS.formatDate(new Date(from)) + ".json", autotuneIob.nsHistorytoJSON().toString(2).replace("\\/", "/"));
                    atLog("Create ns-treatments." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folder");
                } catch (JSONException e) {}

                //AutotunePrep autotunePrep = new AutotunePrep();
                preppedGlucose = autotunePrep.categorizeBGDatums(autotuneIob,tunedProfile, pumpprofile);
                //Todo philoul create dedicated function including log in AutotuneFS
                AutotuneFS.createAutotunefile("aaps-autotune." + AutotuneFS.formatDate(new Date(from)) + ".json", preppedGlucose.toString(2));
                atLog("file aaps-autotune." + AutotuneFS.formatDate(new Date(from)) + ".json created in " + AutotuneFS.AUTOTUNEFOLDER + " folder");

                tunedProfile = autotuneCore.tuneAllTheThings(preppedGlucose, tunedProfile, pumpprofile);

                AutotuneFS.createAutotunefile("newprofile." + AutotuneFS.formatDate(new Date(from)) + ".json", tunedProfile.profiletoOrefJSON());
                atLog("Create newprofile." + AutotuneFS.formatDate(new Date(from)) + ".json file in " + AutotuneFS.AUTOTUNEFOLDER + " folders");
//                autotuneFragment.updateResult("day " + i +" / "+ daysBack + " tuned");
            }
        }

        if(tunedProfile.profile != null) {
            DecimalFormat df = new DecimalFormat("0.000");
            DecimalFormat ef = new DecimalFormat("00");
            String line = "-----------------------------------------------\n";


            //Todo add Strings and format for all results presentation
            //Todo Replace % of modification by number of missing days for each hour
            //May be we could remove CSF from the list...
            result = line;
            // show ISF CR and CSF
            result += "|  ISF |   " + Round.roundTo(pumpprofile.isf / toMgDl, 0.1) + "   |    " + Round.roundTo(tunedProfile.isf / toMgDl,0.1)+"   |\n";
            result += line;
            result += "|  CR  |     " + Round.roundTo(pumpprofile.ic,0.1) + "   |      " + Round.roundTo(tunedProfile.ic,0.1) + "   |\n";
            //result += line;
            //result += "| CSF | " + Round.roundTo(pumpprofile.isf / pumpprofile.ic / toMgDl, 0.001) + "  |  " + Round.roundTo(tunedProfile.isf / tunedProfile.ic / toMgDl,0.001) + "  |\n";
            result += line;
            result += "|Hour| Profile | Tuned |nbKo|  %   |\n";
            result += line;
            double totalBasal = 0d;
            double totalTuned = 0d;
            for (int i = 0; i < 24; i++) {
                String basalString = df.format(pumpprofile.basal[i]);
                String tunedString = df.format(tunedProfile.basal[i]);
                totalBasal +=pumpprofile.basal[i];
                totalTuned +=tunedProfile.basal[i];
                int percentageChangeValue = (int) ((tunedProfile.basal[i]/pumpprofile.basal[i]) * 100 - 100) ;
                String percentageChange;
                if (percentageChangeValue == 0)
                    percentageChange = "   0  ";
                else if (percentageChangeValue < 0)
                    percentageChange = " " + percentageChangeValue;
                else
                    percentageChange = "+" + percentageChangeValue;
                if (percentageChangeValue != 0)
                    percentageChange += "%";


                result += "|  " + ef.format(i) + "  |  " + basalString + "  |  " + tunedString + "  |  " + ef.format(tunedProfile.basalUntuned[i]) + "  | " + percentageChange + " |\n";

            }
            result += line;
            result += "|  Som  |  " + Round.roundTo(totalBasal,0.1) + "  |  " + Round.roundTo(totalTuned,0.1) + "  |\n";
            result += line;

            AutotuneFS.createAutotunefile(AutotuneFS.RECOMMENDATIONS,result);

            try {

                AutotuneFS.createAutotunefile(resourceHelper.gs(R.string.autotune_tunedprofile_name) + ".json", tunedProfile.getData().toString(2).replace("\\/","/"),true);

                //store.put(resourceHelper.gs(R.string.autotune_tunedprofile_name), convertedProfile);
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

    // end of autotune Plugin
    public void atLog(String message){
        aapsLogger.debug(LTag.AUTOTUNE,message);
        log.debug(message);                         // for debugging to have log even if Autotune Log disabled
        logString += message +"\n";                 // for log file in autotune folder even if autotune log disable

    }
}
