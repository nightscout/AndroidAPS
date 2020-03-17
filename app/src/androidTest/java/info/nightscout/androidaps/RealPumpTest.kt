package info.nightscout.androidaps

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin
import info.nightscout.androidaps.plugins.insulin.InsulinOrefUltraRapidActingPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.source.RandomBgPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.isRunningTest
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory

@LargeTest
@RunWith(AndroidJUnit4::class)
class RealPumpTest {

    private val log = LoggerFactory.getLogger(L.CORE)

    companion object {
        val pump: PumpInterface = DanaRv2Plugin.getPlugin()
        const val R_PASSWORD = 1234
        const val R_SERIAL = "PBB00013LR_P"
    }

    private val validProfile = "{\"dia\":\"6\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"10\"},{\"time\":\"2:00\",\"value\":\"11\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

    @Rule
    @JvmField
    var mActivityTestRule = ActivityTestRule(MainActivity::class.java)

    @Rule
    @JvmField
    var mGrantPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

    @Before
    fun clear() {
        SP.clear()
        SP.putBoolean(R.string.key_setupwizard_processed, true)
        SP.putString(R.string.key_aps_mode, "closed")
        MainApp.getDbHelper().resetDatabases()
        MainApp.devBranch = false
    }

    private fun preparePlugins() {
        // Source
        RandomBgPlugin.performPluginSwitch(true, PluginType.BGSOURCE)
        // Profile
        LocalProfilePlugin.performPluginSwitch(true, PluginType.PROFILE)
        val profile = Profile(JSONObject(validProfile), Constants.MGDL)
        Assert.assertTrue(profile.isValid("Test"))
        LocalProfilePlugin.profiles.clear()
        LocalProfilePlugin.numOfProfiles = 0
        val singleProfile = LocalProfilePlugin.SingleProfile().copyFrom(profile, "TestProfile")
        LocalProfilePlugin.addProfile(singleProfile)
        ProfileFunctions.doProfileSwitch(LocalProfilePlugin.createProfileStore(), "TestProfile", 0, 100, 0, DateUtil.now())
        // Insulin
        InsulinOrefUltraRapidActingPlugin.getPlugin().performPluginSwitch(true, PluginType.INSULIN)
        // Pump
        SP.putInt(R.string.key_danar_password, R_PASSWORD)
        SP.putString(R.string.key_danar_bt_name, R_SERIAL)
        (pump as PluginBase).performPluginSwitch(true, PluginType.PUMP)
        // Sensitivity
        SensitivityOref1Plugin.getPlugin().performPluginSwitch(true, PluginType.SENSITIVITY)
        // APS
        OpenAPSSMBPlugin.getPlugin().performPluginSwitch(true, PluginType.APS)
        LoopPlugin.getPlugin().performPluginSwitch(true, PluginType.LOOP)

        // Enable common
        ActionsPlugin.performPluginSwitch(true, PluginType.GENERAL)

        // Disable unneeded
        MainApp.getPluginsList().remove(ObjectivesPlugin)
    }

    @Test
    fun doTest() {
        Assert.assertTrue(isRunningTest())
        preparePlugins()

        while (!pump.isInitialized) {
            log.debug("Waiting for initialization")
            SystemClock.sleep(1000)
        }

        while (true) {
            log.debug("Tick")
            SystemClock.sleep(1000)
        }
    }
}