package app.aaps.implementation.maintenance.migration

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.tempTargets.toTTPresets
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The migrations that decide whether an old backup restores, or silently loses the user's settings.
 *
 * Nothing else in the tree can catch a mistake here. These names cannot be produced by any current
 * build, the keys they write are read much later by other code, and a function that quietly matches
 * nothing fails no test and logs nothing - which is exactly how the `OpenAPSSMBDynamicISFPlugin`
 * migration did nothing at all from January 2024 until somebody read it.
 *
 * In `androidHostTest` rather than `commonTest` only because [PersistenceLayer] is mocked, and Mockito
 * is JVM only. The class under test is `commonMain` and compiles for every platform.
 */
class PreferenceMigrationsTest {

    private val persistenceLayer = mock<PersistenceLayer>()
    private val dateUtil = mock<DateUtil>()
    private val profileUtil = mock<ProfileUtil>()

    private fun sut(client: Boolean = false, aps: Boolean = true): PreferenceMigrations {
        val config = mock<Config>()
        whenever(config.AAPSCLIENT).thenReturn(client)
        whenever(config.APS).thenReturn(aps)
        whenever(dateUtil.now()).thenReturn(1_700_000_000_000L)
        return PreferenceMigrations(mock<AAPSLogger>(), config, persistenceLayer, dateUtil, profileUtil)
    }

    private fun storeOf(vararg pairs: Pair<String, Any?>) = FileKeyValueStore(mapOf(*pairs))

    private val schedule = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":3}]"

    // ----- The renames -----

    @Test fun `activity monitor counters are renamed`() = runTest {
        val store = storeOf("Monitor_Overview_total" to 42L, "Monitor_Overview_resumed" to 7L, "Monitor_Overview_start" to 9L)

        sut().migrate(store)

        assertThat(store.getLong("Monitor_total_Overview", 0L)).isEqualTo(42L)
        assertThat(store.getLong("Monitor_resumed_Overview", 0L)).isEqualTo(7L)
        assertThat(store.getLong("Monitor_start_Overview", 0L)).isEqualTo(9L)
        assertThat(store.contains("Monitor_Overview_total")).isFalse()
    }

    @Test fun `objectives progress is renamed`() = runTest {
        val store = storeOf("Objectives_config_started" to 1L, "Objectives_config_accomplished" to 2L)

        sut().migrate(store)

        assertThat(store.getLong("Objectives_started_config", 0L)).isEqualTo(1L)
        assertThat(store.getLong("Objectives_accomplished_config", 0L)).isEqualTo(2L)
    }

    @Test fun `plugin selection is renamed and the visibility flag is dropped`() = runTest {
        val store = storeOf("ConfigBuilder_PUMP_DanaRSPlugin_Enabled" to true, "ConfigBuilder_PUMP_DanaRSPlugin_Visible" to true)

        sut().migrate(store)

        assertThat(store.getBoolean("ConfigBuilder_Enabled_PUMP_DanaRSPlugin", false)).isTrue()
        assertThat(store.contains("ConfigBuilder_PUMP_DanaRSPlugin_Enabled")).isFalse()
        assertThat(store.contains("ConfigBuilder_PUMP_DanaRSPlugin_Visible")).isFalse()
    }

    @Test fun `a whole profile is renamed field by field`() = runTest {
        val store = storeOf(
            "LocalProfile_0_name" to "Adult", "LocalProfile_0_mgdl" to false, "LocalProfile_0_isf" to schedule,
            "LocalProfile_0_ic" to schedule, "LocalProfile_0_basal" to schedule,
            "LocalProfile_0_targetlow" to schedule, "LocalProfile_0_targethigh" to schedule
        )

        sut().migrate(store)

        assertThat(store.getString("LocalProfile_name_0", "")).isEqualTo("Adult")
        assertThat(store.getBoolean("LocalProfile_mgdl_0", true)).isFalse()
        listOf("isf", "ic", "basal", "targetlow", "targethigh").forEach { field ->
            assertThat(store.getString("LocalProfile_${field}_0", "")).isEqualTo(schedule)
        }
    }

    /**
     * The raw `_name` key must survive. `MainApp` reads it with `_dia` to pair a profile with the DIA
     * it was using, and only drops both once `dataMigrations()` has stamped the insulin records.
     */
    @Test fun `the raw profile name key is copied across rather than moved`() = runTest {
        val store = storeOf("LocalProfile_0_name" to "Adult", "LocalProfile_0_dia" to 6.0)

        sut().migrate(store)

        assertThat(store.getString("LocalProfile_name_0", "")).isEqualTo("Adult")
        assertThat(store.contains("LocalProfile_0_name")).isTrue()
        assertThat(store.contains("LocalProfile_0_dia")).isTrue()
    }

    @Test fun `the widget black background flag is renamed and the opacity is left alone`() = runTest {
        val store = storeOf("appwidget_use_black_0" to true, "appwidget_0" to 50)

        sut().migrate(store)

        assertThat(store.getBoolean("widget_use_black_0", false)).isTrue()
        assertThat(store.getInt("appwidget_0", 0)).isEqualTo(50)
    }

    @Test fun `tidepool credentials and the session they belonged to are cleared together`() = runTest {
        val store = storeOf("tidepool_username" to "a", "tidepool_password" to "b", "tidepool_auth_state" to "c")

        sut().migrate(store)

        assertThat(store.getAll()).isEmpty()
    }

    /** A working OAuth session with no legacy pair beside it must not be wiped. */
    @Test fun `a tidepool session without legacy credentials is untouched`() = runTest {
        val store = storeOf("tidepool_auth_state" to "live")

        sut().migrate(store)

        assertThat(store.getString("tidepool_auth_state", "")).isEqualTo("live")
    }

    // ----- A migration may do more than move a key -----

    /**
     * The loop mode became a database record, so its migration writes one.
     *
     * This is the case that shows why a migration is a function rather than a rename: without it an
     * imported backup comes back with the loop OFF, because `RM.DEFAULT_MODE` is `DISABLED_LOOP`.
     */
    @Test fun `the loop mode becomes a running mode record`() = runTest {
        val store = storeOf("aps_mode" to "LGS")

        sut().migrate(store)

        val captor = argumentCaptor<RM>()
        verify(persistenceLayer).insertOrUpdateRunningMode(captor.capture(), any(), any(), anyOrNull(), any())
        assertThat(captor.firstValue.mode).isEqualTo(RM.Mode.CLOSED_LOOP_LGS)
        assertThat(store.contains("aps_mode")).isFalse()
    }

    @Test fun `no loop mode record is written on a build that does not loop`() = runTest {
        val store = storeOf("aps_mode" to "CLOSED")

        sut(aps = false).migrate(store)

        verify(persistenceLayer, never()).insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any())
        assertThat(store.contains("aps_mode")).isTrue()
    }

    // ----- Skip, never guess -----

    /**
     * The check that only bites over an import file, and the one with the highest stakes. On a device
     * a schedule cannot be a number, because the stored value would not be text. In a file every value
     * IS text, so `"5.5"` would otherwise be written as this profile's insulin sensitivity schedule.
     */
    @Test fun `a profile schedule that is not an array is left alone`() = runTest {
        val store = storeOf("LocalProfile_0_isf" to "5.5")

        sut().migrate(store)

        assertThat(store.contains("LocalProfile_isf_0")).isFalse()
        assertThat(store.contains("LocalProfile_0_isf")).isTrue()
    }

    /**
     * The one migration that used to destroy what it could not convert: it removed the legacy name
     * outside the null check, so an unreadable value was logged as skipped and then deleted anyway.
     */
    @Test fun `an unreadable widget flag is left in place rather than deleted`() = runTest {
        val store = storeOf("appwidget_use_black_0" to "maybe")

        sut().migrate(store)

        assertThat(store.contains("appwidget_use_black_0")).isTrue()
        assertThat(store.contains("widget_use_black_0")).isFalse()
    }

    @Test fun `a value that will not convert never becomes a substitute`() = runTest {
        val store = storeOf("Monitor_Overview_total" to "not a number")

        sut().migrate(store)

        assertThat(store.contains("Monitor_total_Overview")).isFalse()
        assertThat(store.contains("Monitor_Overview_total")).isTrue()
    }

    // ----- Properties of the whole pass -----

    /**
     * The claim the whole design rests on: the SAME functions, over a device store and over an import
     * file, produce the same answer.
     *
     * A device holds native types; a file holds only text, because the exporter writes
     * `value.toString()` on every entry. If these two ever disagree, an import quietly does something
     * different from an upgrade and nothing else here would notice.
     */
    @Test fun `native values and their text form migrate identically`() = runTest {
        val native = mapOf<String, Any?>(
            "Monitor_Overview_total" to 42L,
            "Objectives_config_started" to 1_700_000_000_000L,
            "ConfigBuilder_PUMP_DanaRSPlugin_Enabled" to true,
            "appwidget_use_black_0" to false,
            "LocalProfile_0_mgdl" to true,
            "LocalProfile_0_name" to "Adult",
            "LocalProfile_0_isf" to schedule,
            "low_mark" to 0.0
        )
        val asText = native.mapValues { (_, value) -> value.toString() }

        val fromDevice = FileKeyValueStore(native).also { sut().migrate(it) }
        val fromFile = FileKeyValueStore(asText).also { sut().migrate(it) }

        assertThat(fromDevice.asTextMap()).isEqualTo(fromFile.asTextMap())
    }

    /** A zero mark is a value 3.3 could leave behind, not a setting. Remove it so the default wins. */
    @Test fun `a zero overview mark is removed and a real one is kept`() = runTest {
        val store = storeOf("low_mark" to 0.0, "high_mark" to 180.0)

        sut().migrate(store)

        assertThat(store.contains("low_mark")).isFalse()
        assertThat(store.getDouble("high_mark", 0.0)).isEqualTo(180.0)
    }

    @Test fun `running twice changes nothing the second time`() = runTest {
        val store = storeOf("Monitor_Overview_total" to 42L, "LocalProfile_0_isf" to schedule, "ConfigBuilder_PUMP_X_Enabled" to true)

        sut().migrate(store)
        val afterFirst = store.asTextMap()
        sut().migrate(store)

        assertThat(store.asTextMap()).isEqualTo(afterFirst)
    }

    /**
     * Nothing in the list invents a value, so an empty store comes out empty.
     *
     * This is what keeps the list safe to run over any file. The two migrations that DO seed -
     * the simple-mode default and the temp target presets - stay in `MainApp` for exactly this reason.
     */
    @Test fun `an empty store gains nothing`() = runTest {
        val store = storeOf()

        sut().migrate(store)

        assertThat(store.getAll()).isEmpty()
        verify(persistenceLayer, never()).insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any())
    }

    @Test fun `a client does not turn dynamic sensitivity on for itself`() = runTest {
        val store = storeOf("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled" to true)

        sut(client = true).migrate(store)

        // The plugin selection still moves across; only the dosing flag is left to the master.
        assertThat(store.getBoolean("ConfigBuilder_Enabled_APS_OpenAPSSMBPlugin", false)).isTrue()
        assertThat(store.contains("use_dynamic_sensitivity")).isFalse()
    }

    /**
     * The pairing the chain exists for, and the one no single migration can get right on its own.
     *
     * A pre-2024 store holds BOTH rows, because only one APS plugin could be on at a time: the retired
     * DynamicISF true, and plain SMB false. [PreferenceMigrations] retires the first by writing the
     * second in its 2024 spelling, and the ConfigBuilder rename - which runs later and reads the store
     * as it finds it - has to carry that `true` across, not the `false` the file started with.
     *
     * Write the composed name in the retirement instead, and this is the test that goes red: the
     * rename arrives afterwards with the stale value and the user ends with no APS plugin on at all.
     */
    @Test fun `retiring dynamic isf survives the later config builder rename`() = runTest {
        val store = storeOf(
            "ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled" to true,
            "ConfigBuilder_APS_OpenAPSSMBPlugin_Enabled" to false
        )

        sut().migrate(store)

        assertThat(store.getBoolean("ConfigBuilder_Enabled_APS_OpenAPSSMBPlugin", false)).isTrue()
        assertThat(store.getBoolean("use_dynamic_sensitivity", false)).isTrue()
        // And no selection row for the plugin that no longer exists.
        assertThat(store.contains("ConfigBuilder_Enabled_APS_OpenAPSSMBDynamicISFPlugin")).isFalse()
    }

    @Test fun `an otp password longer than one is cleared`() = runTest {
        val store = storeOf("smscommunicator_otp_password" to "a-whole-master-password")

        sut().migrate(store)

        assertThat(store.getString("smscommunicator_otp_password", "x")).isEmpty()
    }

    @Test fun `a dyn isf factor stored as text becomes a number`() = runTest {
        val store = storeOf("DynISFAdjust" to "120")

        sut().migrate(store)

        assertThat(store.getInt("DynISFAdjust", 0)).isEqualTo(120)
        assertThat(store.getAll()["DynISFAdjust"]).isInstanceOf(Int::class.javaObjectType)
    }

    @Test fun `a dyn isf factor already an int is left alone`() = runTest {
        val store = storeOf("DynISFAdjust" to 90)

        sut().migrate(store)

        assertThat(store.getInt("DynISFAdjust", 0)).isEqualTo(90)
    }

    // ----- Temp target presets -----

    /**
     * Each target goes through the house decoder, and the durations come across as minutes.
     *
     * The stub is the assertion: the migration must hand the RAW stored number to
     * [ProfileUtil.convertToMgdlDetect] and use what comes back, rather than converting on its own.
     * The maths itself belongs to `ProfileUtilImplTest.convertToMgdlDetect`.
     */
    @Test fun `every preset target is decoded by profile util`() = runTest {
        val store = storeOf(
            "units" to "mmol",
            "eatingsoon_target" to "5.5", "eatingsoon_duration" to "30",
            "activity_target" to "8.3", "activity_duration" to "120",
            "hypo_target" to "9.4", "hypo_duration" to "45"
        )
        whenever(profileUtil.convertToMgdlDetect(5.5)).thenReturn(99.0858)
        whenever(profileUtil.convertToMgdlDetect(8.3)).thenReturn(149.5295)
        whenever(profileUtil.convertToMgdlDetect(9.4)).thenReturn(169.3465)

        sut().migrate(store)

        val presets = store.getString("temp_target_presets", "").toTTPresets()
        assertThat(presets.map { it.id }).containsExactly("eatingsoon", "activity", "hypo").inOrder()
        assertThat(presets[0].targetValue).isWithin(0.01).of(99.09)
        assertThat(presets[1].targetValue).isWithin(0.01).of(149.53)
        assertThat(presets[2].targetValue).isWithin(0.01).of(169.35)
        assertThat(presets.map { it.duration }).containsExactly(1_800_000L, 7_200_000L, 2_700_000L).inOrder()
    }

    /**
     * The regression this decoder exists for, and an ordinary user history rather than a broken file.
     *
     * Set eating-soon to 100 while the display is mg/dL and 3.3 stores `100.0`. Switch the display to
     * mmol/L and `units` becomes `mmol` while the stored number is never rewritten - a `UnitDoubleKey`
     * was put raw and read back by magnitude. A migration that believes `units` multiplies by 18 and
     * makes the target 1801 mg/dL.
     */
    @Test fun `a target saved in mgdl is not multiplied because units now say mmol`() = runTest {
        val store = storeOf("units" to "mmol", "eatingsoon_target" to "100.0", "eatingsoon_duration" to "30")
        whenever(profileUtil.convertToMgdlDetect(100.0)).thenReturn(100.0)

        sut().migrate(store)

        val presets = store.getString("temp_target_presets", "").toTTPresets()
        assertThat(presets.single().targetValue).isEqualTo(100.0)
        verify(profileUtil).convertToMgdlDetect(100.0)
    }

}
