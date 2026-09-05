package app.aaps.plugins.aps.autotune

import android.view.View
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutotunePluginTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var autotuneIob: AutotuneIob
    @Mock lateinit var autotunePrep: AutotunePrep
    @Mock lateinit var autotuneCore: AutotuneCore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var loop: Loop
    private lateinit var autotunePlugin: AutotunePlugin

    /** Distinct from [someICfg] (peak 60) so tests can tell which of the two sources was used. */
    private val inForceICfg = ICfg(insulinLabel = "InForce", insulinEndTime = 5 * 3600 * 1000, insulinPeakTime = 45 * 60 * 1000, concentration = 1.0)

    /** No weekday selected => calcDays() == 0, so the run exits cleanly right after the insulin is resolved. */
    private val noDays = BooleanArray(7) { false }

    @BeforeEach fun prepare() {
        val atProfileProvider = {
            ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger)
        }
        autotunePlugin = AutotunePlugin(
            aapsLogger = aapsLogger,
            rh = rh,
            preferences = preferences,
            rxBus = rxBus,
            profileFunction = profileFunction,
            profileUtil = profileUtil,
            dateUtil = dateUtil,
            profileRepository = profileRepository,
            autotuneFS = autotuneFS,
            autotuneIob = autotuneIob,
            autotunePrep = autotunePrep,
            autotuneCore = autotuneCore,
            config = config,
            uel = uel,
            loop = loop,
            profileStoreProvider = { profileStoreProvider() },
            atProfileProvider = atProfileProvider
        )
        runBlocking {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            whenever(profileFunction.getProfileName()).thenReturn(TESTPROFILENAME)
            whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        }
        whenever(rh.gs(R.string.autotune_tunedprofile_name)).thenReturn("Tuned")
        whenever(rh.gs(R.string.autotune_error_more_days)).thenReturn("Not enough days")
        whenever(rh.gs(app.aaps.core.ui.R.string.profile_switch_no_insulin)).thenReturn("No insulin in use")
    }

    /** The peak autotune actually ran with, read back out of the oref0 settings it exported. */
    private fun exportedPeak(): Int {
        val captor = argumentCaptor<String>()
        verify(autotuneFS).exportSettings(captor.capture())
        return JSONObject(captor.firstValue).getInt("insulinPeakTime")
    }

    // A profile taken from the store is a ProfileSealed.Pure, whose iCfg is always null — insulin lives on the
    // switch, not on the stored profile. Tuning a named profile is the normal case, so falling back to the insulin
    // in force is what keeps it working at all.
    @Test fun `tuning a stored profile falls back to the insulin in force`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)

        autotunePlugin.aapsAutotune(daysBack = 1, autoSwitch = false, profileToTune = TESTPROFILENAME, weekDays = noDays)

        assertThat(exportedPeak()).isEqualTo(inForceICfg.peak)
    }

    // With no profile name the running EffectiveProfile is tuned, and that one does carry its own insulin — it
    // describes the history being reconstructed, so it must win over whatever is merely in force now.
    @Test fun `the tuned profile's own insulin wins over the one in force`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)

        autotunePlugin.aapsAutotune(daysBack = 1, autoSwitch = false, profileToTune = "", weekDays = noDays)

        assertThat(exportedPeak()).isEqualTo(someICfg.peak)
        assertThat(someICfg.peak).isNotEqualTo(inForceICfg.peak) // guards the test itself against the two colliding
    }

    // Nothing to tune against and nobody to ask: refuse before doing any work rather than pick an arbitrary insulin.
    @Test fun `refuses when neither the profile nor anything in force has an insulin`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)

        autotunePlugin.aapsAutotune(daysBack = 1, autoSwitch = false, profileToTune = TESTPROFILENAME, weekDays = noDays)

        assertThat(autotunePlugin.result).isEqualTo("No insulin in use")
        assertThat(autotunePlugin.calculationRunning).isFalse()
        verify(autotuneFS, never()).exportSettings(any())
    }

    /** Drives a complete one-day run so the trailing autoSwitch block is reached. */
    private suspend fun runOneTunedDay(autoSwitch: Boolean) {
        val tuned = ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger).with(validProfile, someICfg)
        whenever(autotuneIob.boluses).thenReturn(arrayListOf(BS(timestamp = 0, amount = 1.0, type = BS.Type.NORMAL, iCfg = someICfg)))
        whenever(autotunePrep.categorize(any())).thenReturn(mock())
        whenever(autotuneCore.tuneAllTheThings(any(), any(), any())).thenReturn(tuned)
        whenever(rh.gs(R.string.autotune_result)).thenReturn("Result %1\$s")
        whenever(rh.gs(R.string.autotune_log_separator)).thenReturn("-")
        whenever(rh.gs(R.string.autotune_log_title)).thenReturn("title")
        // A name absent from the store keeps updateProfile() on its "add a new profile" branch, which needs no
        // pre-populated profile list. The empty profileToTune then tunes the running EffectiveProfile.
        whenever(profileFunction.getProfileName()).thenReturn("NotInStore")
        whenever(profileRepository.copyFrom(any(), any())).thenReturn(mock())
        autotunePlugin.aapsAutotune(daysBack = 1, autoSwitch = autoSwitch, profileToTune = "", weekDays = BooleanArray(7) { true })
    }

    // The auto-switch is unattended, so with nothing in force it is skipped rather than stamped with an arbitrary
    // insulin — but the tuned profile then never got applied, so the manual button has to come back.
    @Test fun `auto switch is skipped and the update button restored when no insulin is in force`() = runBlocking {
        // The tuned EffectiveProfile carries its own iCfg, so the only lookup here is the auto-switch one.
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)

        runOneTunedDay(autoSwitch = true)

        verify(profileFunction, never()).createProfileSwitch(
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
        )
        assertThat(autotunePlugin.result).isEqualTo("No insulin in use")
        assertThat(autotunePlugin.updateButtonVisibility).isEqualTo(View.VISIBLE)
    }

    // Counterpart to the skip above: autotune tunes basal/IC/ISF only, so the switch it writes carries the insulin
    // already in force unchanged.
    @Test fun `auto switch keeps the insulin in force`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)

        runOneTunedDay(autoSwitch = true)

        val captor = argumentCaptor<ICfg>()
        verify(profileFunction).createProfileSwitch(
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), captor.capture()
        )
        assertThat(captor.firstValue).isEqualTo(inForceICfg)
    }
}
