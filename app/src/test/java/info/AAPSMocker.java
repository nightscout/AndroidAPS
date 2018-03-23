package info;

import android.content.Context;

import com.squareup.otto.Bus;

import org.json.JSONException;
import org.json.JSONObject;
import org.powermock.api.mockito.PowerMockito;

import java.util.Locale;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
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
    static String validProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    static Profile profile;

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

    public static Profile getValidProfile() throws JSONException {
        if (profile == null)
            profile = new Profile(new JSONObject(validProfile), Constants.MGDL);
        return profile;
    }
}
