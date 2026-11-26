package app.aaps.database

import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InsulinConfiguration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The second half of keeping the DIA of a 3.x AAPSClient (#5625).
 *
 * `migration32to33` keeps the DIA of a row that had no peak and marks the peak -1 (see
 * `InsulinConfigMigration33Test`). `MainApp.dataMigrations` then fills the peak with these queries. Run against
 * a real database file, because the rule is in the SQL.
 */
class InsulinPeakFillTest {

    private val directory = createTempDirectory("aaps-insulin-peak-test")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")
    private val repository = AppRepository { database }

    @AfterTest
    fun tearDown() {
        repository.close()
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a profile switch with its own DIA gets the peak and keeps the DIA`() = runTest {
        val noPeak = database.profileSwitchDao.insert(profileSwitch(NO_PEAK))
        val complete = database.profileSwitchDao.insert(profileSwitch(LYUMJEV_6H))
        val sentinel = database.profileSwitchDao.insert(profileSwitch(SENTINEL))
        // An old version of a record, which the bulk stamp leaves alone too
        val history = database.profileSwitchDao.insert(profileSwitch(NO_PEAK).also { it.referenceId = complete })

        val filled = repository.fillMissingProfileSwitchInsulinPeak(RAPID, PEAK_75M)

        assertThat(filled).isEqualTo(1)
        assertThat(database.profileSwitchDao.findById(noPeak)?.insulinConfiguration).isEqualTo(InsulinConfiguration(RAPID, DIA_5H, PEAK_75M, 1.0))
        // A complete row is the record's own insulin, and a row with nothing is the job of the bulk stamp.
        assertThat(database.profileSwitchDao.findById(complete)?.insulinConfiguration).isEqualTo(LYUMJEV_6H)
        assertThat(database.profileSwitchDao.findById(sentinel)?.insulinConfiguration).isEqualTo(SENTINEL)
        assertThat(database.profileSwitchDao.findById(history)?.insulinConfiguration).isEqualTo(NO_PEAK)
    }

    @Test
    fun `an effective profile switch with its own DIA gets the peak and keeps the DIA`() = runTest {
        val noPeak = database.effectiveProfileSwitchDao.insert(effectiveProfileSwitch(NO_PEAK))
        val complete = database.effectiveProfileSwitchDao.insert(effectiveProfileSwitch(LYUMJEV_6H))
        val sentinel = database.effectiveProfileSwitchDao.insert(effectiveProfileSwitch(SENTINEL))

        val filled = repository.fillMissingEffectiveProfileSwitchInsulinPeak(RAPID, PEAK_75M)

        assertThat(filled).isEqualTo(1)
        assertThat(database.effectiveProfileSwitchDao.findById(noPeak)?.insulinConfiguration).isEqualTo(InsulinConfiguration(RAPID, DIA_5H, PEAK_75M, 1.0))
        assertThat(database.effectiveProfileSwitchDao.findById(complete)?.insulinConfiguration).isEqualTo(LYUMJEV_6H)
        assertThat(database.effectiveProfileSwitchDao.findById(sentinel)?.insulinConfiguration).isEqualTo(SENTINEL)
    }

    private fun profileSwitch(insulin: InsulinConfiguration) = ProfileSwitch(
        timestamp = TIME, basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL, profileName = "Adult", timeshift = 0, percentage = 100, duration = 0,
        insulinConfiguration = insulin.copy()
    )

    private fun effectiveProfileSwitch(insulin: InsulinConfiguration) = EffectiveProfileSwitch(
        timestamp = TIME, basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL, originalProfileName = "Adult", originalCustomizedName = "Adult", originalTimeshift = 0,
        originalPercentage = 100, originalDuration = 0, originalEnd = 0, insulinConfiguration = insulin.copy()
    )

    private companion object {

        const val TIME = 1_700_000_000_000L
        const val RAPID = "Rapid-Acting Oref"
        const val DIA_5H = 5L * 3600 * 1000
        const val DIA_6H = 6L * 3600 * 1000
        const val PEAK_45M = 45L * 60 * 1000
        const val PEAK_75M = 75L * 60 * 1000

        /** What `migration32to33` makes of a 3.x client's row: its own DIA, the peak missing. */
        val NO_PEAK = InsulinConfiguration("", DIA_5H, -1, 1.0)
        val LYUMJEV_6H = InsulinConfiguration("Lyumjev", DIA_6H, PEAK_45M, 1.0)
        val SENTINEL = InsulinConfiguration("", -1, -1, 1.0)
    }
}
