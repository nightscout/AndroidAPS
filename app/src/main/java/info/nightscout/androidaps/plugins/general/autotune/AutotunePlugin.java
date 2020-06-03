package info.nightscout.androidaps.plugins.general.autotune;

import android.content.Context;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
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
import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Initialized by Rumen Georgiev on 1/29/2018.
 idea is to port Autotune from OpenAPS to java
 let's start by taking 1 day of data from NS, and comparing it to ideal BG

 * Update by philoul on 05/2020 (complete refactor of AutotunePlugin)
 *  TODO: add Preference for main settings (may be advanced settings for % of adjustment (default 20%))
 *  Manage ProfileSwitch once Results validated (Still lots of work!)
 *
 */

@Singleton
public class AutotunePlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(AutotunePlugin.class);
    private static Profile profile;

    private String logString="";

    public static final int autotuneStartHour = 4;
    public static String result="";
    public static Date lastRun=null;
    public static String lastNbDays="";
    public ATProfile tunedProfile;
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
    private AutotuneFS autotuneFS;
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

    //Todo futur version: add profile selector in AutotuneFragment to allow running autotune plugin with other profiles than current
    public String aapsAutotune(int daysBack) {
        //todo add autotuneFS, autotunePrep and autotuneCore in constructor (but I can't manage to do that, something probably wrong in dagger settings for these class ???)
        autotuneFS = new AutotuneFS(injector);
        autotunePrep = new AutotunePrep(injector);
        autotuneCore = new AutotuneCore(injector);
        lastNbDays = "" + daysBack;
        autotuneIob = new AutotuneIob(injector);

        atLog("Start Autotune with " + daysBack + " days back");

        //create autotune subfolder for autotune files if not exists
        autotuneFS.createAutotuneFolder();
        logString="";
        //clean autotune folder before run
        autotuneFS.deleteAutotuneFiles();

        long now = System.currentTimeMillis();
        lastRun = new Date(System.currentTimeMillis());
        // Today at 4 AM
        //Note, I don't find on existing function the equivalent in DateUtil so I created a new function, but if any equivalent function already exists may be we can use it
        long endTime = DateUtil.toTimeMinutesFromMidnight(now, autotuneStartHour*60);
        // Check if 4 AM is before now
        if (endTime > now)
            endTime -= 24 * 60 * 60 * 1000L;
        long starttime = endTime - daysBack * 24 * 60 *  60 * 1000L;

        autotuneFS.exportSettings(settings(lastRun,daysBack,new Date(starttime),new Date(endTime)));

        profile = profileFunction.getProfile(now);

        tunedProfile = new ATProfile(profile);
        tunedProfile.profilename=resourceHelper.gs(R.string.autotune_tunedprofile_name);
        ATProfile pumpprofile = new ATProfile(profile);
        pumpprofile.profilename=profileFunction.getProfileName();

        autotuneFS.exportPumpProfile(pumpprofile);

        if(daysBack < 1){
            //todo add string
            return "Sorry I cannot do it for less than 1 day!";
        } else {
            // todo Add loop below (including result presentation) in a dedicated thread
            for (int i = 0; i < daysBack; i++) {
                // get 24 hours BG values from 4 AM to 4 AM next day
                long from = starttime + i * 24 * 60 * 60 * 1000L;
                long to = from + 24 * 60 * 60 * 1000L;

                atLog("Tune day "+ (i+1) +" of "+ daysBack);

                //autotuneIob contains BG and Treatments data from history (<=> query for ns-treatments and ns-entries)
                autotuneIob.initializeData(from, to);
                //<=> ns-entries.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                autotuneFS.exportEntries(autotuneIob);
                //<=> ns-treatments.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine (include treatments ,tempBasal and extended
                autotuneFS.exportTreatments(autotuneIob);

                preppedGlucose = autotunePrep.categorizeBGDatums(autotuneIob,tunedProfile, pumpprofile);
                //<=> autotune.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                if (preppedGlucose == null )
                    return "Error in input data";

                autotuneFS.exportPreppedGlucose(preppedGlucose);

                tunedProfile = autotuneCore.tuneAllTheThings(preppedGlucose, tunedProfile, pumpprofile);
                //<=> newprofile.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                autotuneFS.exportTunedProfile(tunedProfile);

                //Todo: if possible add feedback to user between each day
                // This was a trial to update fragment results between each day (autotune calculation takes about 2 minutes for 30 days...)
//                autotuneFragment.updateResult("day " + i +" / "+ daysBack + " tuned");
            }
        }

        if(tunedProfile.profile != null) {

            result = showResults(tunedProfile,pumpprofile);

            autotuneFS.exportResult(result);

            // TODO: integration with ProfileStore to develop below some part of previous code...
            //store.put(resourceHelper.gs(R.string.autotune_tunedprofile_name), convertedProfile);
            //ProfileStore profileStore = new ProfileStore(json);
            //sp.putString("autotuneprofile", profileStore.getData().toString());
            //log.debug("Entered in ProfileStore "+profileStore.getSpecificProfile(MainApp.gs(R.string.tuneprofile_name)));
            //     RxBus.INSTANCE.send(new EventProfileStoreChanged());

            // Export log file (can be compared with Oref0 log file and zip all autotune files created during the run.
            autotuneFS.exportLogAndZip(lastRun, logString);

            return result;
        } else
            return "No Result";
    }

    private String showResults(ATProfile tunedProfile, ATProfile pumpProfile) {
        int toMgDl = 1;
        if(profileFunction.getUnits().equals("mmol"))
            toMgDl = 18;

        String strResult = "";
        DecimalFormat df = new DecimalFormat("0.000");
        DecimalFormat df2 = new DecimalFormat("0.00");
        DecimalFormat ef = new DecimalFormat("00");
        String line = "---------------------------------------------------\n";

        //Todo add Strings and format for all results presentation
        //Todo Replace % of modification by number of missing days for each hour
        //I don't work on this part of cade just do some improvement of existing code, probably to be reworked...
        strResult = line;
        // show ISF and CR
        strResult += "|  ISF | " + df2.format(pumpProfile.isf / toMgDl) + " |  " + df2.format(tunedProfile.isf / toMgDl)+" |\n";
        strResult += line;
        strResult += "|  IC  | " + df2.format(pumpProfile.ic) + "  | " + df2.format(tunedProfile.ic) + " |\n";
        strResult += line;
        strResult += "|Hour| Profile | Tuned |Miss.days/%\n";
        strResult += line;
        double totalBasal = 0d;
        double totalTuned = 0d;
        for (int i = 0; i < 24; i++) {
            String basalString = df2.format(pumpProfile.basal[i]);
            String tunedString = df2.format(tunedProfile.basal[i]);
            totalBasal +=pumpProfile.basal[i];
            totalTuned +=tunedProfile.basal[i];
            int percentageChangeValue = (int) ((tunedProfile.basal[i]/pumpProfile.basal[i]) * 100 - 100) ;
            String percentageChange;
            if (percentageChangeValue == 0)
                percentageChange = "0%";
            else if (percentageChangeValue < 0)
                percentageChange = "" + percentageChangeValue + "%";
            else
                percentageChange = "+" + percentageChangeValue +"%";

            strResult += "|  " + ef.format(i) + "  |  " + basalString + "  |  " + tunedString + "  |  " + tunedProfile.basalUntuned[i] + " / " + percentageChange + "\n";
        }
        strResult += line;
        strResult += "|   ∑    |   " + Round.roundTo(totalBasal,0.1) + "   |   " + Round.roundTo(totalTuned,0.1) + "   |\n";
        strResult += line;

        atLog(strResult);
        return strResult;
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
