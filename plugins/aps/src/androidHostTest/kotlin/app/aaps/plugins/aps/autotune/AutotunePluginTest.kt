package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.keys.AutotuneStringKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
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
            atProfileProvider = atProfileProvider,
            notificationManager = mock()
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
        // The result table. Asked as TextRef now, and the shared stubs refuse a string no test stubbed.
        whenever(rh.gs(R.string.autotune_log_ic_isf)).thenReturn("ic_isf")
        whenever(rh.gs(R.string.autotune_log_basal)).thenReturn("basal")
        whenever(rh.gs(R.string.autotune_log_sum_basal)).thenReturn("sum")
        whenever(rh.gs(app.aaps.core.ui.R.string.isf_short)).thenReturn("ISF")
        whenever(rh.gs(app.aaps.core.ui.R.string.ic_short)).thenReturn("IC")
        whenever(rh.gs(app.aaps.core.ui.R.string.autotune)).thenReturn("Autotune")
        whenever(rh.gs(app.aaps.core.ui.R.string.invalid_profile)).thenReturn("Invalid profile")
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
        assertThat(autotunePlugin.updateButtonVisibility).isEqualTo(AutotunePlugin.UPDATE_BUTTON_SHOWN)
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

    // A refused switch (here: the pump cannot take the tuned basal) left the tuned profile stored but not in force,
    // with no button and nothing said. Like the skipped switch above, say why and bring the button back.
    @Test fun `a refused auto switch says why and restores the update button`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)
        // createProfileSwitch is not stubbed, so it refuses (null).
        whenever(profileFunction.profileSwitchRefusal(any(), any(), any(), any(), any(), any())).thenReturn("The pump cannot take it")

        runOneTunedDay(autoSwitch = true)

        assertThat(autotunePlugin.result).isEqualTo("The pump cannot take it")
        assertThat(autotunePlugin.updateButtonVisibility).isEqualTo(AutotunePlugin.UPDATE_BUTTON_SHOWN)
    }

    // A day with no glucose values gives no prepared data. The run must end with an error, as for a day with no
    // treatments, and not go on as if the day was tuned.
    @Test fun `a day with no prepared data ends the run with an error`() = runBlocking {
        whenever(autotuneIob.boluses).thenReturn(arrayListOf(BS(timestamp = 0, amount = 1.0, type = BS.Type.NORMAL, iCfg = someICfg)))
        whenever(autotunePrep.categorize(any())).thenReturn(null)
        whenever(rh.gs(R.string.autotune_error)).thenReturn("Autotune error")

        autotunePlugin.aapsAutotune(daysBack = 1, autoSwitch = false, profileToTune = "", weekDays = BooleanArray(7) { true })

        assertThat(autotunePlugin.result).isEqualTo("Autotune error")
        assertThat(autotunePlugin.calculationRunning).isFalse()
        verify(autotuneCore, never()).tuneAllTheThings(any(), any(), any())
        verify(autotuneFS, never()).zipAutotune(any())
    }

    // The settings file is for running oref0 autotune on a computer with the same data, so its text is pinned:
    // the dates, the zone and the commands must stay what they are.
    @Test fun `the oref0 settings file`() {
        inZone("Europe/Prague") {
            runBlocking {
                whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)
                whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("https://my.nightscout/site")
                whenever(preferences.get(BooleanKey.AutotuneCategorizeUamAsBasal)).thenReturn(true)

                autotunePlugin.aapsAutotune(daysBack = 3, autoSwitch = false, profileToTune = TESTPROFILENAME, weekDays = noDays)

                val captor = argumentCaptor<String>()
                verify(autotuneFS).exportSettings(captor.capture())
                AutotuneGolden.assertMatches("settings.json", AutotuneGolden.json(captor.firstValue))
            }
        }
    }

    /** The last run as it was saved, read back as JSON. */
    private fun savedLastRun(): JsonObject {
        val captor = argumentCaptor<String>()
        verify(preferences, atLeastOnce()).put(eq(AutotuneStringKey.AutotuneLastRun), captor.capture())
        return Json.parseToJsonElement(captor.lastValue).jsonObject
    }

    // The saved last run keeps the button state as a number. Older builds wrote 0 for shown and 8 for hidden, and a
    // saved run is read back after an update, so the numbers must not change.
    @Test fun `a run without auto switch saves the update button as shown`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)

        runOneTunedDay(autoSwitch = false)

        assertThat(savedLastRun()["updateButtonVisibility"]?.jsonPrimitive?.int).isEqualTo(0)
    }

    @Test fun `a run with a done auto switch saves the update button as hidden`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)
        whenever(
            profileFunction.createProfileSwitch(
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
            )
        ).thenReturn(profileSwitch)

        runOneTunedDay(autoSwitch = true)

        assertThat(savedLastRun()["updateButtonVisibility"]?.jsonPrimitive?.int).isEqualTo(8)
    }

    @Test fun `a saved run with a hidden button reads back hidden`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(inForceICfg)
        whenever(
            profileFunction.createProfileSwitch(
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
            )
        ).thenReturn(profileSwitch)
        runOneTunedDay(autoSwitch = true)
        val saved = savedLastRun().toString()
        whenever(preferences.get(AutotuneStringKey.AutotuneLastRun)).thenReturn(saved)

        autotunePlugin.updateButtonVisibility = AutotunePlugin.UPDATE_BUTTON_SHOWN
        autotunePlugin.loadLastRun()

        assertThat(autotunePlugin.updateButtonVisibility).isEqualTo(AutotunePlugin.UPDATE_BUTTON_HIDDEN)
        assertThat(autotunePlugin.lastRunSuccess).isTrue()
    }
}
