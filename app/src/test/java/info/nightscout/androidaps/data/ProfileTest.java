package info.nightscout.androidaps.data;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Calendar;
import java.util.TimeZone;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.FabricPrivacy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, VirtualPumpPlugin.class, FabricPrivacy.class})
public class ProfileTest extends Profile {

    PumpInterface pump = new VirtualPumpPlugin();
    String validProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String belowLimitValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.001\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String notStartingAtZeroValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:30\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String noUnitsValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\"}";
    String wrongProfile = "{\"dia\":\"3\",\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";

    //String profileStore = "{\"defaultProfile\":\"Default\",\"store\":{\"Default\":" + validProfile + "}}";

    boolean notificationBelowLimitSent = false;

    @Test
    public void doTests() throws Exception {
        prepareMock();


        // Test valid profile
        init(new JSONObject(validProfile), 100, 0);
        Assert.assertEquals(true, isValid("Test"));
        Assert.assertEquals(true, log().contains("NS units: mmol"));
        JSONAssert.assertEquals(validProfile, getData(), false);
        Assert.assertEquals(3.0d, getDia(), 0.01d);
        Assert.assertEquals(TimeZone.getTimeZone("UTC"), getTimeZone());
        Assert.assertEquals("00:30", format_HH_MM(30 * 60));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 1);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Assert.assertEquals(100d, getIsf(c.getTimeInMillis()), 0.01d);
        c.set(Calendar.HOUR_OF_DAY, 2);
        Assert.assertEquals(110d, getIsf(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(110d, getIsfTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    100,0 mmol/U\n" + "02:00    110,0 mmol/U", getIsfList());
        Assert.assertEquals(30d, getIc(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(30d, getIcTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    30,0 g/U", getIcList());
        Assert.assertEquals(0.1d, getBasal(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(0.1d, getBasalTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    0,10 U/h", getBasalList());
        Assert.assertEquals(0.1d, getBasalValues()[0].value);
        Assert.assertEquals(0.1d, getMaxDailyBasal());
        Assert.assertEquals(2.4d, percentageBasalSum(), 0.01d);
        Assert.assertEquals(2.4d, baseBasalSum(), 0.01d);
        Assert.assertEquals(4.5d, getTarget(2 * 60 * 60), 0.01d);
        Assert.assertEquals(4d, getTargetLow(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(4d, getTargetLowTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals(5d, getTargetHigh(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(5d, getTargetHighTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    4,0 - 5,0 mmol", getTargetList());
        Assert.assertEquals(100, getPercentage());
        Assert.assertEquals(0, getTimeshift());

        Assert.assertEquals(0.1d, toMgdl(0.1d, Constants.MGDL));
        Assert.assertEquals(18d, toMgdl(1d, Constants.MMOL));
        Assert.assertEquals(1d, toMmol(18d, Constants.MGDL));
        Assert.assertEquals(18d, toMmol(18d, Constants.MMOL));
        Assert.assertEquals(18d, fromMgdlToUnits(18d, Constants.MGDL));
        Assert.assertEquals(1d, fromMgdlToUnits(18d, Constants.MMOL));
        Assert.assertEquals(18d, toUnits(18d, 1d, Constants.MGDL));
        Assert.assertEquals(1d, toUnits(18d, 1d, Constants.MMOL));
        Assert.assertEquals("18", toUnitsString(18d, 1d, Constants.MGDL));
        Assert.assertEquals("1,0", toUnitsString(18d, 1d, Constants.MMOL));
        Assert.assertEquals("5 - 6", toTargetRangeString(5d, 6d, Constants.MGDL, Constants.MGDL));

        //Test basal profile below limit
        Assert.assertEquals(false, notificationBelowLimitSent);
        init(new JSONObject(belowLimitValidProfile), 100, 0);
        isValid("Test");
        Assert.assertEquals(true, notificationBelowLimitSent);


        // Test profile w/o units
        init(new JSONObject(noUnitsValidProfile), 100, 0);
        Assert.assertEquals(null, getUnits());
        Profile nup = new Profile(new JSONObject(noUnitsValidProfile), Constants.MMOL);
        Assert.assertEquals(Constants.MMOL, nup.getUnits());
        // failover to MGDL
        nup = new Profile(new JSONObject(noUnitsValidProfile), null);
        Assert.assertEquals(Constants.MGDL, nup.getUnits());

        //Test profile not starting at midnight
        init(new JSONObject(notStartingAtZeroValidProfile), 100, 0);
        Assert.assertEquals(30.0d, getIc(0), 0.01d);

        // Test wrong profile
        init(new JSONObject(wrongProfile), 100, 0);
        Assert.assertEquals(false, isValid("Test"));

        // Test percentage functionality
        init(new JSONObject(validProfile), 50, 0);
        Assert.assertEquals(0.05d, getBasal(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(1.2d, percentageBasalSum(), 0.01d);
        Assert.assertEquals(60d, getIc(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(220d, getIsf(c.getTimeInMillis()), 0.01d);

        // Test timeshift functionality
        init(new JSONObject(validProfile), 100, 1);
        Assert.assertEquals(
                "00:00    110,0 mmol/U\n" +
                        "01:00    100,0 mmol/U\n" +
                        "03:00    110,0 mmol/U", getIsfList());
    }

    private void prepareMock() throws Exception {
        ConfigBuilderPlugin configBuilderPlugin = mock(ConfigBuilderPlugin.class);
        PowerMockito.mockStatic(ConfigBuilderPlugin.class);

        MainApp mainApp = mock(MainApp.class);
        PowerMockito.mockStatic(MainApp.class);
        when(MainApp.instance()).thenReturn(mainApp);
        when(MainApp.getConfigBuilder()).thenReturn(configBuilderPlugin);
        when(MainApp.getConfigBuilder().getActivePump()).thenReturn(pump);

        PowerMockito.mockStatic(FabricPrivacy.class);
//        PowerMockito.doNothing().when(FabricPrivacy.log(""));

        Bus bus = new Bus(ThreadEnforcer.ANY);
        when(MainApp.bus()).thenReturn(bus);
    }

    @Override
    protected void sendBelowMinimumNotification(String from) {
        notificationBelowLimitSent = true;
    }
}
