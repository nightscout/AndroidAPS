package info.nightscout.androidaps.data

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.FabricPrivacy
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(VirtualPumpPlugin::class, FabricPrivacy::class)
class ProfileTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    var okProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    var belowLimitValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.001\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    var notAllignedBasalValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:30\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    var notStartingAtZeroValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:30\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    var noUnitsValidProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\"}"
    var wrongProfile = "{\"dia\":\"3\",\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

    //String profileStore = "{\"defaultProfile\":\"Default\",\"store\":{\"Default\":" + validProfile + "}}";

    val pumpDescription = PumpDescription()

    @Before
    fun prepare() {
        `when`(activePluginProvider.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(resourceHelper.gs(R.string.profile_per_unit)).thenReturn("/U")
        `when`(resourceHelper.gs(R.string.profile_carbs_per_unit)).thenReturn("g/U")
        `when`(resourceHelper.gs(R.string.profile_ins_units_per_hour)).thenReturn("U/h")
    }

    @Test
    fun doTests() {

        // Test valid profile
        var p: Profile = Profile(profileInjector, JSONObject(okProfile), 100, 0)
        Assert.assertEquals(true, p.isValid("Test"))
        Assert.assertEquals(true, p.log().contains("NS units: mmol"))
        JSONAssert.assertEquals(okProfile, p.data, false)
        Assert.assertEquals(3.0, p.dia, 0.01)
        Assert.assertEquals(TimeZone.getTimeZone("UTC"), p.timeZone)
        Assert.assertEquals("00:30", Profile.format_HH_MM(30 * 60))
        val c = Calendar.getInstance()
        c[Calendar.HOUR_OF_DAY] = 1
        c[Calendar.MINUTE] = 0
        c[Calendar.SECOND] = 0
        c[Calendar.MILLISECOND] = 0
        Assert.assertEquals(1800.0, p.getIsfMgdl(c.timeInMillis), 0.01)
        c[Calendar.HOUR_OF_DAY] = 2
        Assert.assertEquals(1980.0, p.getIsfMgdl(c.timeInMillis), 0.01)
        Assert.assertEquals(110.0, p.getIsfTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("""
    00:00    100,0 mmol/U
    02:00    110,0 mmol/U
    """.trimIndent(), p.isfList.replace(".", ","))
        Assert.assertEquals(30.0, p.getIc(c.timeInMillis), 0.01)
        Assert.assertEquals(30.0, p.getIcTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("00:00    30,0 g/U", p.icList.replace(".", ","))
        Assert.assertEquals(0.1, p.getBasal(c.timeInMillis), 0.01)
        Assert.assertEquals(0.1, p.getBasalTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("00:00    0,10 U/h", p.basalList.replace(".", ","))
        Assert.assertEquals(0.1, p.basalValues[0].value, 0.01)
        Assert.assertEquals(0.1, p.maxDailyBasal, 0.01)
        Assert.assertEquals(2.4, p.percentageBasalSum(), 0.01)
        Assert.assertEquals(2.4, p.baseBasalSum(), 0.01)
        Assert.assertEquals(81.0, p.getTargetMgdl(2 * 60 * 60), 0.01)
        Assert.assertEquals(72.0, p.getTargetLowMgdl(c.timeInMillis), 0.01)
        Assert.assertEquals(4.0, p.getTargetLowTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals(90.0, p.getTargetHighMgdl(c.timeInMillis), 0.01)
        Assert.assertEquals(5.0, p.getTargetHighTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("00:00    4,0 - 5,0 mmol", p.targetList.replace(".", ","))
        Assert.assertEquals(100, p.percentage)
        Assert.assertEquals(0, p.timeshift)
        Assert.assertEquals(0.1, Profile.toMgdl(0.1, Constants.MGDL), 0.01)
        Assert.assertEquals(18.0, Profile.toMgdl(1.0, Constants.MMOL), 0.01)
        Assert.assertEquals(1.0, Profile.toMmol(18.0, Constants.MGDL), 0.01)
        Assert.assertEquals(18.0, Profile.toMmol(18.0, Constants.MMOL), 0.01)
        Assert.assertEquals(18.0, Profile.fromMgdlToUnits(18.0, Constants.MGDL), 0.01)
        Assert.assertEquals(1.0, Profile.fromMgdlToUnits(18.0, Constants.MMOL), 0.01)
        Assert.assertEquals(18.0, Profile.toUnits(18.0, 1.0, Constants.MGDL), 0.01)
        Assert.assertEquals(1.0, Profile.toUnits(18.0, 1.0, Constants.MMOL), 0.01)
        Assert.assertEquals("18", Profile.toUnitsString(18.0, 1.0, Constants.MGDL))
        Assert.assertEquals("1,0", Profile.toUnitsString(18.0, 1.0, Constants.MMOL).replace(".", ","))
        Assert.assertEquals("5 - 6", Profile.toTargetRangeString(5.0, 6.0, Constants.MGDL, Constants.MGDL))
        Assert.assertEquals("4", Profile.toTargetRangeString(4.0, 4.0, Constants.MGDL, Constants.MGDL))

        //Test basal profile below limit
        p = Profile(profileInjector, JSONObject(belowLimitValidProfile), 100, 0)
        p.isValid("Test")
        //Assert.assertEquals(true, ((AAPSMocker.MockedBus) MainApp.bus()).notificationSent);

        // Test profile w/o units
        p = Profile(profileInjector, JSONObject(noUnitsValidProfile), 100, 0)
        Assert.assertEquals(null, p.units)
        p = Profile(profileInjector, JSONObject(noUnitsValidProfile), Constants.MMOL)
        Assert.assertEquals(Constants.MMOL, p.units)
        // failover to MGDL
        p = Profile(profileInjector, JSONObject(noUnitsValidProfile), null)
        Assert.assertEquals(Constants.MGDL, p.units)

        //Test profile not starting at midnight
        p = Profile(profileInjector, JSONObject(notStartingAtZeroValidProfile), 100, 0)
        Assert.assertEquals(30.0, p.getIc(0), 0.01)

        // Test wrong profile
        p = Profile(profileInjector, JSONObject(wrongProfile), 100, 0)
        Assert.assertEquals(false, p.isValid("Test"))

        // Test percentage functionality
        p = Profile(profileInjector, JSONObject(okProfile), 50, 0)
        Assert.assertEquals(0.05, p.getBasal(c.timeInMillis), 0.01)
        Assert.assertEquals(1.2, p.percentageBasalSum(), 0.01)
        Assert.assertEquals(60.0, p.getIc(c.timeInMillis), 0.01)
        Assert.assertEquals(3960.0, p.getIsfMgdl(c.timeInMillis), 0.01)

        // Test timeshift functionality
        p = Profile(profileInjector, JSONObject(okProfile), 100, 1)
        Assert.assertEquals(
            """
                00:00    110.0 mmol/U
                01:00    100.0 mmol/U
                03:00    110.0 mmol/U
                """.trimIndent(), p.isfList)

        // Test hour alignment
        pumpDescription.is30minBasalRatesCapable = false
        //((AAPSMocker.MockedBus) MainApp.bus()).notificationSent = false;
        p = Profile(profileInjector, JSONObject(notAllignedBasalValidProfile), 100, 0)
        p.isValid("Test")
        //Assert.assertEquals(true, ((AAPSMocker.MockedBus) MainApp.bus()).notificationSent);
    }
}