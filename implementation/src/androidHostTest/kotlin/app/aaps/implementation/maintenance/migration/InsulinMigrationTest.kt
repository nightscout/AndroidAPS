package app.aaps.implementation.maintenance.migration

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The v3 to v4 insulin migration: which insulin gets stamped onto the records that predate ICfg.
 *
 * This is the one migration nothing could catch. Everything else in `MainApp.doMigrations` was moved into
 * [PreferenceMigrations] and covered by [PreferenceMigrationsTest]; the insulin half stayed in `MainApp`,
 * which has no test and cannot have one. Two defects lived there as a result, and both are pinned below:
 *
 * - a DIA that silently became the hard-limit maximum of 9 h whenever the running record's profile name
 *   could not be matched - a renamed profile, a Nightscout profile switch, or simply no active record. The
 *   other harvested DIAs were deleted unused. It is one-way, so the wrong value is never corrected.
 * - the two sources asked in the wrong order: the legacy preferences came first and the record's own
 *   insulin only when nothing had been harvested, so an install whose row knew the answer got a
 *   reconstruction anyway.
 *
 * The defaulting to [InsulinType.OREF_RAPID_ACTING] when no ConfigBuilder row is found looks like a third
 * defect and is not one. Checked on a real 3.4.2.1 build running on an emulator: a v3 install writes no
 * `ConfigBuilder_*INSULIN*` key at all until the user changes the selection, and that plugin is
 * `enableByDefault(true)`, so "no row" really does mean rapid-acting. Returning "unknown" there would move
 * those users' historical peak from 75 to 55 min.
 *
 * In `androidHostTest` rather than `commonTest` only because [HardLimits] is mocked and Mockito is JVM
 * only. The class under test is `commonMain` and compiles for every platform.
 */
class InsulinMigrationTest {

    /** Real English, so an asserted nickname is the text a user actually sees. */
    private val rh = generatedTextResolver()

    private fun sut(): InsulinMigration {
        val hardLimits = mock<HardLimits>()
        // The adult range. Its top is the value the old code reached for whenever it could not find a DIA.
        whenever(hardLimits.diaRange()).thenReturn(5.0..9.0)
        return InsulinMigration(mock<AAPSLogger>(), hardLimits, rh)
    }

    private fun storeOf(vararg pairs: Pair<String, Any?>) = FileKeyValueStore(mapOf(*pairs))

    // ── readLegacyInsulin: which plugin a v3 install had selected ───────────────────────────────────

    @Test fun `no insulin plugin key means v3's own default of rapid acting`() {
        // Verified against a real 3.4.2.1 install: it writes no ConfigBuilder insulin key until the user
        // changes the selection, and InsulinOrefRapidActingPlugin is enableByDefault(true). So this is
        // reading v3's default, not inventing one - and the flag says the row was absent.
        val legacy = sut().readLegacyInsulin(storeOf("some_other_key" to true))

        assertThat(legacy.nickname).isEqualTo(rh.gs(InsulinType.OREF_RAPID_ACTING.label))
        assertThat(legacy.peakTime).isEqualTo(PEAK_75M)
        assertThat(legacy.fromExplicitKey).isFalse()
    }

    @Test fun `a plugin key set to false also means the default`() {
        val legacy = sut().readLegacyInsulin(storeOf("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin" to false))

        assertThat(legacy.peakTime).isEqualTo(PEAK_75M)
        assertThat(legacy.fromExplicitKey).isFalse()
    }

    @Test fun `the renamed config builder key is read`() {
        val store = storeOf("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin" to true)

        val legacy = sut().readLegacyInsulin(store)

        assertThat(legacy).isEqualTo(LegacyInsulin(rh.gs(InsulinType.OREF_LYUMJEV.label), InsulinType.OREF_LYUMJEV.insulinPeakTime, fromExplicitKey = true))
    }

    @Test fun `the pre rename config builder key is read too`() {
        // Both spellings, so it does not matter whether PreferenceMigrations.configBuilder has been past.
        val store = storeOf("ConfigBuilder_INSULIN_InsulinOrefUltraRapidActingPlugin_Enabled" to true)

        val legacy = sut().readLegacyInsulin(store)

        assertThat(legacy?.peakTime).isEqualTo(InsulinType.OREF_ULTRA_RAPID_ACTING.insulinPeakTime)
    }

    @Test fun `free peak takes the peak the user set`() {
        val store = storeOf("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin" to true, "insulin_oref_peak" to 55)

        assertThat(sut().readLegacyInsulin(store)?.peakTime).isEqualTo(55L * 60 * 1000)
    }

    @Test fun `free peak reads a peak stored as text`() {
        // The v3 preference screen was an edit box, so the value can be text rather than an Int.
        val store = storeOf("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin" to true, "insulin_oref_peak" to "55")

        assertThat(sut().readLegacyInsulin(store)?.peakTime).isEqualTo(55L * 60 * 1000)
    }

    @Test fun `free peak without a stored peak uses the v3 default`() {
        val store = storeOf("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin" to true)

        assertThat(sut().readLegacyInsulin(store)?.peakTime).isEqualTo(75L * 60 * 1000)
    }

    // ── harvestProfileDia: the DIA each v3 profile was using ────────────────────────────────────────

    @Test fun `the raw 3 3 profile names are harvested`() {
        val store = storeOf("LocalProfile_0_name" to "Adult", "LocalProfile_0_dia" to 6.0)

        val harvest = sut().harvestProfileDia(store)

        assertThat(harvest.nameToDia).containsExactly("Adult", 6.0)
        assertThat(harvest.keysToRemove).containsExactly("LocalProfile_0_name", "LocalProfile_0_dia")
    }

    @Test fun `the renamed profile names are harvested`() {
        val store = storeOf("LocalProfile_name_0" to "Adult", "LocalProfile_dia_0" to 6.0)

        val harvest = sut().harvestProfileDia(store)

        assertThat(harvest.nameToDia).containsExactly("Adult", 6.0)
        // The renamed `_name` key is a live key this build still reads, so only the dead DIA key goes.
        assertThat(harvest.keysToRemove).containsExactly("LocalProfile_dia_0")
    }

    @Test fun `a DIA stored as text or as a float is read`() {
        val store = storeOf(
            "LocalProfile_name_0" to "Adult", "LocalProfile_dia_0" to "5.5",
            "LocalProfile_name_1" to "Child", "LocalProfile_dia_1" to 6.5f
        )

        val harvest = sut().harvestProfileDia(store)

        assertThat(harvest.nameToDia).containsExactly("Adult", 5.5, "Child", 6.5)
    }

    @Test fun `a profile with no DIA is not harvested`() {
        val store = storeOf("LocalProfile_name_0" to "Adult")

        assertThat(sut().harvestProfileDia(store).nameToDia).isEmpty()
    }

    @Test fun `a malformed index is skipped rather than taken for profile zero`() {
        val store = storeOf("LocalProfile_name_x" to "Nonsense", "LocalProfile_dia_x" to 9.9)

        assertThat(sut().harvestProfileDia(store).nameToDia).isEmpty()
    }

    // ── plan: the record wins, then the legacy keys, then a declared guess ──────────────────────────

    @Test fun `the insulin on the record itself wins`() {
        // After migration32to33 carries the v3 columns across, this is the usual case: the row knows.
        val record = ICfg("Rapid-Acting Oref", DIA_6H, PEAK_75M, 1.0)

        val plan = sut().plan(record, legacyRapid(), harvestOf("Adult" to 5.0), activeProfileName = "Adult")

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.RECORD)
        assertThat(plan.iCfg).isEqualTo(record)
        assertThat(plan.isGuess).isFalse()
    }

    @Test fun `a record still carrying the sentinel is not used`() {
        // The v33 sentinel reports a DIA of 0.0. Reading it back would write the sentinel over itself and
        // leave the rows unmigrated for ever, with the profile reporting a zero DIA.
        val sentinel = ICfg("", -1, -1, 1.0)

        val plan = sut().plan(sentinel, legacyRapid(), harvestOf("Adult" to 6.0), activeProfileName = "Adult")

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.LEGACY_KEYS)
        assertThat(plan.iCfg.dia).isEqualTo(6.0)
    }

    @Test fun `the running profile's own harvested DIA is used`() {
        val plan = sut().plan(null, legacyRapid(), harvestOf("Adult" to 6.0, "Child" to 5.0), activeProfileName = "Adult")

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.LEGACY_KEYS)
        assertThat(plan.iCfg.dia).isEqualTo(6.0)
        assertThat(plan.iCfg.insulinPeakTime).isEqualTo(PEAK_75M)
    }

    @Test fun `the only profile's DIA is used when its name does not match`() {
        // The fix. One local profile is the usual setup, so the answer is certain even though the running
        // record's name cannot be matched - it was renamed, or the switch came from Nightscout. This used
        // to fall straight to 9 h, silently, over the user's whole history.
        val plan = sut().plan(null, legacyRapid(), harvestOf("Adult" to 6.0), activeProfileName = "Renamed")

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.LEGACY_KEYS)
        assertThat(plan.iCfg.dia).isEqualTo(6.0)
    }

    @Test fun `profiles that agree on the DIA answer for a name that does not match`() {
        val plan = sut().plan(null, legacyRapid(), harvestOf("Adult" to 6.0, "Child" to 6.0), activeProfileName = null)

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.LEGACY_KEYS)
        assertThat(plan.iCfg.dia).isEqualTo(6.0)
    }

    @Test fun `profiles that disagree with no name to match fall back to the hard limit and say so`() {
        val plan = sut().plan(null, legacyRapid(), harvestOf("Adult" to 6.0, "Child" to 5.0), activeProfileName = null)

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.GUESSED_DIA)
        assertThat(plan.iCfg.dia).isEqualTo(9.0)
        // The insulin is still the right one - only the DIA is a guess - and the user has to be told.
        assertThat(plan.iCfg.insulinPeakTime).isEqualTo(PEAK_75M)
        assertThat(plan.isGuess).isTrue()
    }

    @Test fun `no insulin row and no DIA is reported as no legacy data`() {
        // What a FRESH install looks like, which is why the caller leaves the insulin list alone on this
        // one. The insulin is still v3's default rather than a different template.
        val noRow = sut().readLegacyInsulin(storeOf())

        val plan = sut().plan(null, noRow, harvestOf(), activeProfileName = null)

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.NO_LEGACY_DATA)
        assertThat(plan.iCfg.insulinPeakTime).isEqualTo(PEAK_75M)
        assertThat(plan.iCfg.dia).isEqualTo(9.0)
        assertThat(plan.isGuess).isTrue()
    }

    @Test fun `an explicit insulin row with no DIA is a guessed DIA not missing data`() {
        // The user picked Lyumjev but no DIA can be found. Only the DIA is invented, so this must not be
        // mistaken for a fresh install - the insulin list does get the real insulin.
        val lyumjev = sut().readLegacyInsulin(storeOf("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin" to true))

        val plan = sut().plan(null, lyumjev, harvestOf(), activeProfileName = null)

        assertThat(plan.source).isEqualTo(InsulinMigrationPlan.Source.GUESSED_DIA)
        assertThat(plan.iCfg.insulinPeakTime).isEqualTo(InsulinType.OREF_LYUMJEV.insulinPeakTime)
    }

    @Test fun `a rebuilt insulin is left unlabelled so the insulin list names it`() {
        val plan = sut().plan(null, legacyRapid(), harvestOf("Adult" to 6.0), activeProfileName = "Adult")

        // One naming rule, in InsulinImpl. Building the label here as well was how the two drifted.
        assertThat(plan.iCfg.insulinLabel).isEmpty()
        assertThat(plan.iCfg.insulinNickname).isEqualTo(rh.gs(InsulinType.OREF_RAPID_ACTING.label))
        assertThat(plan.iCfg.concentration).isEqualTo(1.0)
    }

    // ── Order: the plugin keys must be read before the preference migrations delete them ────────────

    @Test fun `reading after the preference migrations loses the selection`() = runTest {
        // Not a defect, a pin. `PreferenceMigrations.insulinPlugins` removes these dead names, so
        // `MainApp.doMigrations` has to read them first. That used to be a comment and nothing else, and a
        // reordering would have cost every upgrading user their insulin selection without failing a test.
        val store = storeOf("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin" to true)
        assertThat(sut().readLegacyInsulin(store).fromExplicitKey).isTrue()

        preferenceMigrations().migrate(store)

        // Lyumjev is gone and the read falls back to v3's default, which for this user is the wrong insulin.
        assertThat(sut().readLegacyInsulin(store).fromExplicitKey).isFalse()
        assertThat(sut().readLegacyInsulin(store).peakTime).isEqualTo(PEAK_75M)
    }

    private fun preferenceMigrations(): PreferenceMigrations {
        val config = mock<Config>()
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(config.APS).thenReturn(true)
        return PreferenceMigrations(mock<AAPSLogger>(), config, mock<PersistenceLayer>(), mock<DateUtil>(), mock<ProfileUtil>())
    }

    private fun legacyRapid() = LegacyInsulin(rh.gs(InsulinType.OREF_RAPID_ACTING.label), PEAK_75M, fromExplicitKey = true)

    private fun harvestOf(vararg pairs: Pair<String, Double>) = ProfileDiaHarvest(mapOf(*pairs), emptyList())

    private companion object {

        const val DIA_6H = 6L * 3600 * 1000
        const val PEAK_75M = 75L * 60 * 1000
    }
}
