package app.aaps.core.objects.extensions

import android.content.Context
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.TestPumpPlugin
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProfileExtensionKtTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var config: Config
    @Mock lateinit var loop: Loop
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData

    private lateinit var profile: Profile

    // target_low == target_high == 5.0 mmol/L → getTargetMgdl() = 5.0 * 18.01559 = 90.078 mg/dL,
    // getRoundedTargetMgdl() = 90.1 (the value the APS actually runs, min_bg/max_bg rounded to 0.1).
    private val mmol5Profile =
        "{\"iCfg\":{\"insulinLabel\":\"\",\"insulinEndTime\":18000000,\"insulinPeakTime\":4500000,\"concentration\":\"1.0\"}," +
            "\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"sens\":[{\"time\":\"00:00\",\"value\":\"6\"}]," +
            "\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}]," +
            "\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

    private val roundedProfileTarget = 90.1  // profile.getRoundedTargetMgdl()

    @BeforeEach
    fun prepare() {
        val dateUtil: DateUtil = DateUtilImpl(context)
        whenever(activePlugin.activePump).thenReturn(TestPumpPlugin(rh))
        profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(mmol5Profile), dateUtil)!!, activePlugin)
    }

    private fun apsResult(targetBg: Double): APSResult {
        val result = mock<APSResult>()
        whenever(result.targetBG).thenReturn(targetBg)
        return result
    }

    private fun lastRunWith(targetBg: Double?): Loop.LastRun =
        Loop.LastRun().apply { constraintsProcessed = targetBg?.let { apsResult(it) } }

    @Test
    fun getRoundedTargetMgdl_roundsToApsGrid() {
        // The bug: getTargetMgdl() is unrounded (90.078) while the APS runs the rounded 90.1,
        // so a plain equality check flagged every loop run as "adjusted".
        assertThat(profile.getTargetMgdl()).isWithin(0.0001).of(90.07795)
        assertThat(profile.getRoundedTargetMgdl()).isWithin(0.0001).of(90.1)
    }

    @Test
    fun apsTargetEqualToRoundedProfileTarget_isNotAdjusted() {
        // Real scenario: the APS builds target_bg = 90.1 from the same rounding → must NOT read as adjusted.
        val lastRun = lastRunWith(roundedProfileTarget)
        whenever(config.APS).thenReturn(true)
        whenever(loop.lastRun).thenReturn(lastRun)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isNull()
    }

    @Test
    fun apsOverridingTarget_isAdjusted() {
        val lastRun = lastRunWith(100.0)
        whenever(config.APS).thenReturn(true)
        whenever(loop.lastRun).thenReturn(lastRun)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isWithin(0.01).of(100.0)
    }

    @Test
    fun client_readsTargetFromDeviceStatus() {
        val result = apsResult(100.0)
        whenever(config.APS).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(processedDeviceStatusData.getAPSResult()).thenReturn(result)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isWithin(0.01).of(100.0)
    }

    @Test
    fun client_targetEqualToRoundedProfileTarget_isNotAdjusted() {
        val result = apsResult(roundedProfileTarget)
        whenever(config.APS).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(processedDeviceStatusData.getAPSResult()).thenReturn(result)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isNull()
    }

    @Test
    fun neitherApsNorClient_isNull() {
        whenever(config.APS).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isNull()
    }

    @Test
    fun noLastRun_isNull() {
        whenever(config.APS).thenReturn(true)
        whenever(loop.lastRun).thenReturn(null)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isNull()
    }

    @Test
    fun zeroTargetBg_isNull() {
        val lastRun = lastRunWith(0.0)
        whenever(config.APS).thenReturn(true)
        whenever(loop.lastRun).thenReturn(lastRun)
        assertThat(profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)).isNull()
    }
}
