package info.nightscout.androidaps.data;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Calendar;
import java.util.TimeZone;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;

import static org.mockito.Mockito.when;

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, VirtualPumpPlugin.class, FabricPrivacy.class})
public class ProfileTest {

    PumpInterface pump = new VirtualPumpPlugin();
    String validProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String belowLimitValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.001\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String notAllignedBasalValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:30\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String notStartingAtZeroValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:30\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    String noUnitsValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\"}";
    String wrongProfile = "{\"dia\":\"3\",\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";

    //String profileStore = "{\"defaultProfile\":\"Default\",\"store\":{\"Default\":" + validProfile + "}}";

    @Test
    public void doTests() throws Exception {
        Profile p = new Profile();

        // Test valid profile
        p = new Profile(new JSONObject(validProfile), 100, 0);
        Assert.assertEquals(true, p.isValid("Test"));
        Assert.assertEquals(true, p.log().contains("NS units: mmol"));
        JSONAssert.assertEquals(validProfile, p.getData(), false);
        Assert.assertEquals(3.0d, p.getDia(), 0.01d);
        Assert.assertEquals(TimeZone.getTimeZone("UTC"), p.getTimeZone());
        Assert.assertEquals("00:30", p.format_HH_MM(30 * 60));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 1);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Assert.assertEquals(100d, p.getIsf(c.getTimeInMillis()), 0.01d);
        c.set(Calendar.HOUR_OF_DAY, 2);
        Assert.assertEquals(110d, p.getIsf(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(110d, p.getIsfTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    100,0 mmol/U\n" + "02:00    110,0 mmol/U", p.getIsfList().replace(".", ","));
        Assert.assertEquals(30d, p.getIc(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(30d, p.getIcTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    30,0 g/U", p.getIcList().replace(".", ","));
        Assert.assertEquals(0.1d, p.getBasal(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(0.1d, p.getBasalTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    0,10 U/h", p.getBasalList().replace(".", ","));
        Assert.assertEquals(0.1d, p.getBasalValues()[0].value);
        Assert.assertEquals(0.1d, p.getMaxDailyBasal());
        Assert.assertEquals(2.4d, p.percentageBasalSum(), 0.01d);
        Assert.assertEquals(2.4d, p.baseBasalSum(), 0.01d);
        Assert.assertEquals(4.5d, p.getTarget(2 * 60 * 60), 0.01d);
        Assert.assertEquals(4d, p.getTargetLow(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(4d, p.getTargetLowTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals(5d, p.getTargetHigh(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(5d, p.getTargetHighTimeFromMidnight(2 * 60 * 60), 0.01d);
        Assert.assertEquals("00:00    4,0 - 5,0 mmol", p.getTargetList().replace(".", ","));
        Assert.assertEquals(100, p.getPercentage());
        Assert.assertEquals(0, p.getTimeshift());

        Assert.assertEquals(0.1d, p.toMgdl(0.1d, Constants.MGDL));
        Assert.assertEquals(18d, p.toMgdl(1d, Constants.MMOL));
        Assert.assertEquals(1d, p.toMmol(18d, Constants.MGDL));
        Assert.assertEquals(18d, p.toMmol(18d, Constants.MMOL));
        Assert.assertEquals(18d, p.fromMgdlToUnits(18d, Constants.MGDL));
        Assert.assertEquals(1d, p.fromMgdlToUnits(18d, Constants.MMOL));
        Assert.assertEquals(18d, p.toUnits(18d, 1d, Constants.MGDL));
        Assert.assertEquals(1d, p.toUnits(18d, 1d, Constants.MMOL));
        Assert.assertEquals("18", p.toUnitsString(18d, 1d, Constants.MGDL));
        Assert.assertEquals("1,0", p.toUnitsString(18d, 1d, Constants.MMOL).replace(".", ","));
        Assert.assertEquals("5 - 6", p.toTargetRangeString(5d, 6d, Constants.MGDL, Constants.MGDL));
        Assert.assertEquals("4", p.toTargetRangeString(4d, 4d, Constants.MGDL, Constants.MGDL));

        //Test basal profile below limit
        p = new Profile(new JSONObject(belowLimitValidProfile), 100, 0);
        p.isValid("Test");
        //Assert.assertEquals(true, ((AAPSMocker.MockedBus) MainApp.bus()).notificationSent);

        // Test profile w/o units
        p = new Profile(new JSONObject(noUnitsValidProfile), 100, 0);
        Assert.assertEquals(null, p.getUnits());
        p = new Profile(new JSONObject(noUnitsValidProfile), Constants.MMOL);
        Assert.assertEquals(Constants.MMOL, p.getUnits());
        // failover to MGDL
        p = new Profile(new JSONObject(noUnitsValidProfile), null);
        Assert.assertEquals(Constants.MGDL, p.getUnits());

        //Test profile not starting at midnight
        p = new Profile(new JSONObject(notStartingAtZeroValidProfile), 100, 0);
        Assert.assertEquals(30.0d, p.getIc(0), 0.01d);

        // Test wrong profile
        p = new Profile(new JSONObject(wrongProfile), 100, 0);
        Assert.assertEquals(false, p.isValid("Test"));

        // Test percentage functionality
        p = new Profile(new JSONObject(validProfile), 50, 0);
        Assert.assertEquals(0.05d, p.getBasal(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(1.2d, p.percentageBasalSum(), 0.01d);
        Assert.assertEquals(60d, p.getIc(c.getTimeInMillis()), 0.01d);
        Assert.assertEquals(220d, p.getIsf(c.getTimeInMillis()), 0.01d);

        // Test timeshift functionality
        p = new Profile(new JSONObject(validProfile), 100, 1);
        Assert.assertEquals(
                "00:00    110.0 mmol/U\n" +
                        "01:00    100.0 mmol/U\n" +
                        "03:00    110.0 mmol/U", p.getIsfList());

        // Test hour alignment
        ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().is30minBasalRatesCapable = false;
        //((AAPSMocker.MockedBus) MainApp.bus()).notificationSent = false;
        p = new Profile(new JSONObject(notAllignedBasalValidProfile), 100, 0);
        p.isValid("Test");
        //Assert.assertEquals(true, ((AAPSMocker.MockedBus) MainApp.bus()).notificationSent);
    }

    @Before
    public void prepareMock() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockStrings();

        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(pump);

        PowerMockito.mockStatic(FabricPrivacy.class);
//        PowerMockito.doNothing().when(FabricPrivacy.log(""));

    }

 }
