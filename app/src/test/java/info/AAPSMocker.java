package info;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.powermock.api.mockito.PowerMockito;

import java.util.Locale;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by mike on 23.03.2018.
 */

public class AAPSMocker {
    private static String validProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    private static Profile profile;
    private static ProfileStore profileStore;
    public static final String TESTPROFILENAME = "someProfile";

    public static CommandQueue queue;
    public static ConfigBuilderPlugin configBuilderPlugin;
    public static ProfileFunctions profileFunctions;
    public static ConstraintChecker constraintChecker;

    public static void mockStrings() {
        Locale.setDefault(new Locale("en", "US"));

        when(MainApp.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.");
        when(MainApp.gs(R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences");
        when(MainApp.gs(R.string.objectivenotstarted)).thenReturn("Objective %d not started");
        when(MainApp.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump");
        when(MainApp.gs(R.string.autosensdisabledinpreferences)).thenReturn("Autosens disabled in preferences");
        when(MainApp.gs(R.string.smbdisabledinpreferences)).thenReturn("SMB disabled in preferences");
        when(MainApp.gs(R.string.limitingbasalratio)).thenReturn("Limiting basal rate to %.2f U/h because of %s");
        when(MainApp.gs(R.string.pumplimit)).thenReturn("pump limit");
        when(MainApp.gs(R.string.itmustbepositivevalue)).thenReturn("it must be positive value");
        when(MainApp.gs(R.string.maxvalueinpreferences)).thenReturn("max value in preferences");
        when(MainApp.gs(R.string.maxbasalmultiplier)).thenReturn("max basal multiplier");
        when(MainApp.gs(R.string.maxdailybasalmultiplier)).thenReturn("max daily basal multiplier");
        when(MainApp.gs(R.string.limitingpercentrate)).thenReturn("Limiting percent rate to %d%% because of %s");
        when(MainApp.gs(R.string.pumplimit)).thenReturn("pump limit");
        when(MainApp.gs(R.string.limitingbolus)).thenReturn("Limiting bolus to %.1f U because of %s");
        when(MainApp.gs(R.string.hardlimit)).thenReturn("hard limit");
        when(MainApp.gs(R.string.key_child)).thenReturn("child");
        when(MainApp.gs(R.string.limitingcarbs)).thenReturn("Limiting carbs to %d g because of %s");
        when(MainApp.gs(R.string.limitingiob)).thenReturn("Limiting IOB to %.1f U because of %s");
        when(MainApp.gs(R.string.pumpisnottempbasalcapable)).thenReturn("Pump is not temp basal capable");
        when(MainApp.gs(R.string.loop)).thenReturn("Loop");
        when(MainApp.gs(R.string.loop_shortname)).thenReturn("LOOP");
        when(MainApp.gs(R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering");
        when(MainApp.gs(R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode");
        when(MainApp.gs(R.string.Glimp)).thenReturn("Glimp");
        when(MainApp.gs(R.string.glucose)).thenReturn("Glucose");
        when(MainApp.gs(R.string.delta)).thenReturn("Delta");
        when(MainApp.gs(R.string.short_avgdelta)).thenReturn("Short avg. delta");
        when(MainApp.gs(R.string.long_avgdelta)).thenReturn("Long avg. delta");
        when(MainApp.gs(R.string.zerovalueinprofile)).thenReturn("Invalid profile: %s");
        when(MainApp.gs(R.string.success)).thenReturn("Success");
        when(MainApp.gs(R.string.enacted)).thenReturn("Enacted");
        when(MainApp.gs(R.string.comment)).thenReturn("Comment");
        when(MainApp.gs(R.string.smb_shortname)).thenReturn("SMB");
        when(MainApp.gs(R.string.canceltemp)).thenReturn("Cancel temp basal");
        when(MainApp.gs(R.string.duration)).thenReturn("Duration");
        when(MainApp.gs(R.string.percent)).thenReturn("Percent");
        when(MainApp.gs(R.string.absolute)).thenReturn("Absolute");
        when(MainApp.gs(R.string.waitingforpumpresult)).thenReturn("Waiting for result");
        when(MainApp.gs(R.string.insulin_unit_shortname)).thenReturn("U");
        when(MainApp.gs(R.string.minimalbasalvaluereplaced)).thenReturn("Basal value replaced by minimal supported value: %1$s");
        when(MainApp.gs(R.string.basalprofilenotaligned)).thenReturn("Basal values not aligned to hours: %s");
        when(MainApp.gs(R.string.minago)).thenReturn("%d min ago");
        when(MainApp.gs(R.string.hoursago)).thenReturn("%.1fh ago");
        when(MainApp.gs(R.string.careportal_profileswitch)).thenReturn("Profile Switch");
        when(MainApp.gs(R.string.configbuilder_insulin)).thenReturn("Insulin");
        when(MainApp.gs(R.string.bolusdelivering)).thenReturn("Delivering 0.0U");
        when(MainApp.gs(R.string.profile_per_unit)).thenReturn("/U");
        when(MainApp.gs(R.string.shortday)).thenReturn("d");
        when(MainApp.gs(R.string.shorthour)).thenReturn("h");
        when(MainApp.gs(R.string.profile_carbs_per_unit)).thenReturn("g/U");
        when(MainApp.gs(R.string.profile_ins_units_per_hour)).thenReturn("U/h");
        when(MainApp.gs(R.string.sms_wrongcode)).thenReturn("Wrong code. Command cancelled.");
        when(MainApp.gs(R.string.sms_iob)).thenReturn("IOB:");
        when(MainApp.gs(R.string.sms_lastbg)).thenReturn("Last BG:");
        when(MainApp.gs(R.string.sms_minago)).thenReturn("%1$dmin ago");
        when(MainApp.gs(R.string.smscommunicator_remotecommandnotallowed)).thenReturn("Remote command is not allowed");
        when(MainApp.gs(R.string.smscommunicator_stopsmswithcode)).thenReturn("To disable the SMS Remote Service reply with code %1$s.\\n\\nKeep in mind that you\\'ll able to reactivate it directly from the AAPS master smartphone only.");
        when(MainApp.gs(R.string.smscommunicator_mealbolusreplywithcode)).thenReturn("To deliver meal bolus %1$.2fU reply with code %2$s.");
        when(MainApp.gs(R.string.smscommunicator_temptargetwithcode)).thenReturn("To set the Temp Target %1$s reply with code %2$s");
        when(MainApp.gs(R.string.smscommunicator_temptargetcancel)).thenReturn("To cancel Temp Target reply with code %1$s");
        when(MainApp.gs(R.string.smscommunicator_stoppedsms)).thenReturn("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone.");
        when(MainApp.gs(R.string.smscommunicator_tt_set)).thenReturn("Target %1$s for %2$d minutes set successfully");
        when(MainApp.gs(R.string.smscommunicator_tt_canceled)).thenReturn("Temp Target canceled successfully");
        when(MainApp.gs(R.string.loopsuspendedfor)).thenReturn("Suspended (%1$d m)");
        when(MainApp.gs(R.string.smscommunicator_loopisdisabled)).thenReturn("Loop is disabled");
        when(MainApp.gs(R.string.smscommunicator_loopisenabled)).thenReturn("Loop is enabled");
        when(MainApp.gs(R.string.wrongformat)).thenReturn("Wrong format");
        when(MainApp.gs(R.string.smscommunicator_loophasbeendisabled)).thenReturn("Loop has been disabled");
        when(MainApp.gs(R.string.smscommunicator_loophasbeenenabled)).thenReturn("Loop has been enabled");
        when(MainApp.gs(R.string.smscommunicator_tempbasalcanceled)).thenReturn("Temp basal canceled");
        when(MainApp.gs(R.string.smscommunicator_loopresumed)).thenReturn("Loop resumed");
        when(MainApp.gs(R.string.smscommunicator_wrongduration)).thenReturn("Wrong duration");
        when(MainApp.gs(R.string.smscommunicator_suspendreplywithcode)).thenReturn("To suspend loop for %1$d minutes reply with code %2$s");
        when(MainApp.gs(R.string.smscommunicator_loopsuspended)).thenReturn("Loop suspended");
        when(MainApp.gs(R.string.smscommunicator_unknowncommand)).thenReturn("Unknown command or wrong reply");
        when(MainApp.gs(R.string.notconfigured)).thenReturn("Not configured");
        when(MainApp.gs(R.string.smscommunicator_profilereplywithcode)).thenReturn("To switch profile to %1$s %2$d%% reply with code %3$s");
        when(MainApp.gs(R.string.profileswitchcreated)).thenReturn("Profile switch created");
        when(MainApp.gs(R.string.smscommunicator_basalstopreplywithcode)).thenReturn("To stop temp basal reply with code %1$s");
        when(MainApp.gs(R.string.smscommunicator_basalpctreplywithcode)).thenReturn("To start basal %1$d%% for %2$d min reply with code %3$s");
        when(MainApp.gs(R.string.smscommunicator_tempbasalset_percent)).thenReturn("Temp basal %1$d%% for %2$d min started successfully");
        when(MainApp.gs(R.string.smscommunicator_basalreplywithcode)).thenReturn("To start basal %1$.2fU/h for %2$d min reply with code %3$s");
        when(MainApp.gs(R.string.smscommunicator_tempbasalset)).thenReturn("Temp basal %1$.2fU/h for %2$d min started successfully");
        when(MainApp.gs(R.string.smscommunicator_extendedstopreplywithcode)).thenReturn("To stop extended bolus reply with code %1$s");
        when(MainApp.gs(R.string.smscommunicator_extendedcanceled)).thenReturn("Extended bolus canceled");
        when(MainApp.gs(R.string.smscommunicator_extendedreplywithcode)).thenReturn("To start extended bolus %1$.2fU for %2$d min reply with code %3$s");
        when(MainApp.gs(R.string.smscommunicator_extendedset)).thenReturn("Extended bolus %1$.2fU for %2$d min started successfully");
        when(MainApp.gs(R.string.smscommunicator_bolusreplywithcode)).thenReturn("To deliver bolus %1$.2fU reply with code %2$s");
        when(MainApp.gs(R.string.smscommunicator_bolusdelivered)).thenReturn("Bolus %1$.2fU delivered successfully");
        when(MainApp.gs(R.string.smscommunicator_remotebolusnotallowed)).thenReturn("Remote bolus not available. Try again later.");
        when(MainApp.gs(R.string.smscommunicator_calibrationreplywithcode)).thenReturn("To send calibration %1$.2f reply with code %2$s");
        when(MainApp.gs(R.string.smscommunicator_calibrationsent)).thenReturn("Calibration sent. Receiving must be enabled in xDrip.");
        when(MainApp.gs(R.string.pumpsuspended)).thenReturn("Pump suspended");
        when(MainApp.gs(R.string.cob)).thenReturn("COB");
        when(MainApp.gs(R.string.value_unavailable_short)).thenReturn("n/a");
        when(MainApp.gs(R.string.starttemptarget)).thenReturn("Start temp target");
        when(MainApp.gs(R.string.stoptemptarget)).thenReturn("Stop temp target");
        when(MainApp.gs(R.string.disableloop)).thenReturn("Disable loop");
        when(MainApp.gs(R.string.enableloop)).thenReturn("Enable loop");
        when(MainApp.gs(R.string.resumeloop)).thenReturn("Resume loop");
        when(MainApp.gs(R.string.suspendloop)).thenReturn("Suspend loop");
        when(MainApp.gs(R.string.pumpNotInitialized)).thenReturn("Pump not initialized!");
        when(MainApp.gs(R.string.increasingmaxbasal)).thenReturn("Increasing max basal value because setting is lower than your max basal in profile");
        when(MainApp.gs(R.string.overview_bolusprogress_delivered)).thenReturn("Delivered");
        when(MainApp.gs(R.string.smscommunicator_mealbolusreplywithcode)).thenReturn("To deliver meal bolus %1$.2fU reply with code %2$s");
        when(MainApp.gs(R.string.smscommunicator_mealbolusdelivered)).thenReturn("Meal Bolus %1$.2fU delivered successfully");
        when(MainApp.gs(R.string.smscommunicator_mealbolusdelivered_tt)).thenReturn("Target %1$s for %2$d minutes");
        when(MainApp.gs(R.string.smscommunicator_carbsreplywithcode)).thenReturn("To enter %1$dg at %2$s reply with code %3$s");
        when(MainApp.gs(R.string.smscommunicator_carbsset)).thenReturn("Carbs %1$dg entered successfully");
    }

    public static MainApp mockMainApp() {
        System.setProperty("disableFirebase", "true");
        PowerMockito.mockStatic(MainApp.class);
        MainApp mainApp = mock(MainApp.class);
        when(MainApp.instance()).thenReturn(mainApp);

        return mainApp;
    }

    public static void mockConfigBuilder() {
        PowerMockito.mockStatic(ConfigBuilderPlugin.class);
        configBuilderPlugin = mock(ConfigBuilderPlugin.class);
        when(ConfigBuilderPlugin.getPlugin()).thenReturn(configBuilderPlugin);
    }

    public static ConstraintChecker mockConstraintsChecker() {
        constraintChecker = mock(ConstraintChecker.class);
        when(MainApp.getConstraintChecker()).thenReturn(constraintChecker);
        return constraintChecker;
    }

    public static void mockSP() {
        PowerMockito.mockStatic(SP.class);
        when(SP.getLong(anyInt(), anyLong())).thenReturn(0L);
        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(false);
        when(SP.getInt(anyInt(), anyInt())).thenReturn(0);
        when(SP.getString(anyInt(), anyString())).thenReturn("");
    }

    public static void mockL() {
        PowerMockito.mockStatic(L.class);
        when(L.isEnabled(any())).thenReturn(true);
    }

    public static void mockNSUpload() {
        PowerMockito.mockStatic(NSUpload.class);
    }

    public static void mockApplicationContext() {
        Context mockedContext = mock(Context.class);
        Resources mResources = mock(Resources.class);
        when(MainApp.instance().getApplicationContext()).thenReturn(mockedContext);
        when(mockedContext.getResources()).thenReturn(mResources);
        PackageManager packageManager = mock(PackageManager.class);
        when(mockedContext.getPackageManager()).thenReturn(packageManager);
    }

    public static DatabaseHelper mockDatabaseHelper() {
        DatabaseHelper databaseHelper = mock(DatabaseHelper.class);
        when(MainApp.getDbHelper()).thenReturn(databaseHelper);
        return databaseHelper;
    }

    public static void mockCommandQueue() {
        queue = mock(CommandQueue.class);
        when(ConfigBuilderPlugin.getPlugin().getCommandQueue()).thenReturn(queue);
    }

    public static TreatmentsPlugin mockTreatmentPlugin() {
        PowerMockito.mockStatic(TreatmentsPlugin.class);
        TreatmentsPlugin treatmentsPlugin = PowerMockito.mock(TreatmentsPlugin.class);
        when(TreatmentsPlugin.getPlugin()).thenReturn(treatmentsPlugin);
        when(treatmentsPlugin.getLastCalculationTreatments()).thenReturn(new IobTotal(0));
        when(treatmentsPlugin.getLastCalculationTempBasals()).thenReturn(new IobTotal(0));

        TreatmentService treatmentService = PowerMockito.mock(TreatmentService.class);
        when(treatmentsPlugin.getService()).thenReturn(treatmentService);
        return treatmentsPlugin;
    }

    public static void mockTreatmentService() {
        TreatmentService treatmentService = PowerMockito.mock(TreatmentService.class);
        try {
            PowerMockito.whenNew(TreatmentService.class).withNoArguments().thenReturn(treatmentService);
        } catch (Exception e) {
        }

    }

    public static Profile getValidProfile() {
        try {
            if (profile == null)
                profile = new Profile(new JSONObject(validProfile), Constants.MGDL);
        } catch (JSONException ignored) {
        }
        return profile;
    }

    public static ProfileStore getValidProfileStore() {
        try {
            if (profileStore == null) {
                JSONObject json = new JSONObject();
                JSONObject store = new JSONObject();
                JSONObject profile = new JSONObject(validProfile);

                json.put("defaultProfile", TESTPROFILENAME);
                json.put("store", store);
                store.put(TESTPROFILENAME, profile);
                profileStore = new ProfileStore(json);
            }
        } catch (JSONException ignored) {
            Assert.fail("getValidProfileStore() failed");
        }
        return profileStore;
    }

    public static void mockProfileFunctions() {
        PowerMockito.mockStatic(ProfileFunctions.class);
        profileFunctions = PowerMockito.mock(ProfileFunctions.class);
        PowerMockito.when(ProfileFunctions.getSystemUnits()).thenReturn(Constants.MGDL);
        PowerMockito.when(ProfileFunctions.getInstance()).thenReturn(profileFunctions);
        profile = getValidProfile();
        PowerMockito.when(ProfileFunctions.getInstance().getProfile()).thenReturn(profile);
        PowerMockito.when(ProfileFunctions.getInstance().getProfileName()).thenReturn(TESTPROFILENAME);
    }

    public static IobCobCalculatorPlugin mockIobCobCalculatorPlugin() {
        PowerMockito.mockStatic(IobCobCalculatorPlugin.class);
        IobCobCalculatorPlugin iobCobCalculatorPlugin = PowerMockito.mock(IobCobCalculatorPlugin.class);
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin()).thenReturn(iobCobCalculatorPlugin);
        Object dataLock = new Object();
        PowerMockito.when(iobCobCalculatorPlugin.getDataLock()).thenReturn(dataLock);
        return iobCobCalculatorPlugin;
    }

}
