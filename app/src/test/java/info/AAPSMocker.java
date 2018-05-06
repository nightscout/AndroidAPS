package info;

import android.content.Context;
import android.content.Intent;

import com.squareup.otto.Bus;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.powermock.api.mockito.PowerMockito;

import java.util.Locale;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentService;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.utils.SP;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

    public static Intent intentSent = null;

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
        when(MainApp.gs(R.string.minimalbasalvaluereplaced)).thenReturn("Basal value replaced by minimal supported value");
        when(MainApp.gs(R.string.basalprofilenotaligned)).thenReturn("Basal values not aligned to hours: %s");
        when(MainApp.gs(R.string.minago)).thenReturn("%d min ago");
        when(MainApp.gs(R.string.hoursago)).thenReturn("%.1fh ago");
        when(MainApp.gs(R.string.careportal_profileswitch)).thenReturn("Profile Switch");
    }

    public static MainApp mockMainApp() {
        PowerMockito.mockStatic(MainApp.class);
        MainApp mainApp = mock(MainApp.class);
        when(MainApp.instance()).thenReturn(mainApp);
        return mainApp;
    }

    public static void mockConfigBuilder() {
        PowerMockito.mockStatic(ConfigBuilderPlugin.class);
        ConfigBuilderPlugin configBuilderPlugin = mock(ConfigBuilderPlugin.class);
        when(MainApp.getConfigBuilder()).thenReturn(configBuilderPlugin);
    }

    public static void mockConstraintsChecker() {
        ConstraintChecker constraintChecker = mock(ConstraintChecker.class);
        when(MainApp.getConstraintChecker()).thenReturn(constraintChecker);
    }

    public static void mockBus() {
        Bus bus = PowerMockito.mock(Bus.class);
        when(MainApp.bus()).thenReturn(bus);
    }

    public static void mockSP() {
        PowerMockito.mockStatic(SP.class);
        when(SP.getLong(anyInt(), anyLong())).thenReturn(0L);
        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(false);
        when(SP.getInt(anyInt(), anyInt())).thenReturn(0);
    }

    public static void mockApplicationContext() {
        Context context = mock(Context.class);
        when(MainApp.instance().getApplicationContext()).thenReturn(context);
    }

    public static void mockDatabaseHelper() {
        DatabaseHelper databaseHelper = mock(DatabaseHelper.class);
        when(MainApp.getDbHelper()).thenReturn(databaseHelper);
    }

    public static void mockCommandQueue() {
        CommandQueue queue = mock(CommandQueue.class);
        when(ConfigBuilderPlugin.getCommandQueue()).thenReturn(queue);
    }

    public static void mockTreatmentService() throws Exception {
        TreatmentService treatmentService = PowerMockito.mock(TreatmentService.class);
        PowerMockito.whenNew(TreatmentService.class).withNoArguments().thenReturn(treatmentService);
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

    private static MockedBus bus = new MockedBus();

    public static void prepareMockedBus() {
        when(MainApp.bus()).thenReturn(bus);
    }

    public static class MockedBus extends Bus {
        public boolean registered = false;
        public boolean notificationSent = false;

        @Override
        public void register(Object event) {
            registered = true;
        }

        @Override
        public void unregister(Object event) {
            registered = false;
        }

        @Override
        public void post(Object event) {
            notificationSent = true;
        }
    }

}
