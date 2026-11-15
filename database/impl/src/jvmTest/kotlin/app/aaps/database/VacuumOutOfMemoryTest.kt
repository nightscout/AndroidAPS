package app.aaps.database

import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.APSResult
import app.aaps.database.entities.DeviceStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * [AppRepository.vacuumDatabase] when the database does not fit in memory.
 *
 * The bundled SQLite is built with `SQLITE_TEMP_STORE=3`: VACUUM builds its copy of the database in
 * memory, always. A 1.6 GB database of a long-time user failed with "out of memory" on every start.
 * SQLite cannot be made to run out of memory in a test (`DEFAULT_MEMSTATUS=0`, so its heap limit is not
 * enforced), so the decision is driven with the error text the real driver gave, and the trim runs on
 * a real database file.
 */
class VacuumOutOfMemoryTest {

    private val directory = createTempDirectory("aaps-vacuum-test")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")
    private val repository = AppRepository { database }
    private val now = Clock.System.now().toEpochMilliseconds()

    @AfterTest
    fun tearDown() {
        repository.close()
        database.close()
        directory.toFile().deleteRecursively()
    }

    // ----- The decision -----

    @Test
    fun `a database that fits is compacted and nothing is trimmed`() = runTest {
        var trims = 0
        assertThat(vacuumOrTrim(vacuum = {}, trim = { trims++ })).isEqualTo(VacuumOutcome.DONE)
        assertThat(trims).isEqualTo(0)
    }

    @Test
    fun `out of memory trims once and tries again`() = runTest {
        var vacuums = 0
        var trims = 0
        val result = vacuumOrTrim(vacuum = { if (vacuums++ == 0) throw outOfMemory() }, trim = { trims++ })

        assertThat(result).isEqualTo(VacuumOutcome.DONE_AFTER_TRIM)
        assertThat(vacuums).isEqualTo(2)
        assertThat(trims).isEqualTo(1)
    }

    @Test
    fun `out of memory even after the trim is reported, not thrown`() = runTest {
        assertThat(vacuumOrTrim(vacuum = { throw outOfMemory() }, trim = {})).isEqualTo(VacuumOutcome.OUT_OF_MEMORY)
    }

    /** A busy database is worth a retry at the next start, and is no reason to delete anything. */
    @Test
    fun `any other failure is thrown and nothing is trimmed`() = runTest {
        var trims = 0
        assertFailsWith<IllegalStateException> {
            vacuumOrTrim(vacuum = { throw IllegalStateException("Error code: 5, message: database is locked") }, trim = { trims++ })
        }
        assertThat(trims).isEqualTo(0)
    }

    @Test
    fun `only code 7 counts as out of memory`() {
        assertThat(outOfMemory().isOutOfMemory()).isTrue()
        assertThat(IllegalStateException("Error code: 70, message: something else").isOutOfMemory()).isFalse()
        assertThat(IllegalStateException("no code").isOutOfMemory()).isFalse()
    }

    // ----- Against the database -----

    @Test
    fun `the trim deletes old APS results and device statuses and keeps the last days`() = runTest {
        val oldTime = now - (AppRepository.VACUUM_TRIM_KEEP_DAYS + 1).days.inWholeMilliseconds
        val recentTime = now - 1.hours.inWholeMilliseconds
        val oldResult = database.apsResultDao.insert(apsResult(oldTime))
        val recentResult = database.apsResultDao.insert(apsResult(recentTime))
        val oldStatus = database.deviceStatusDao.insert(deviceStatus(oldTime))
        val recentStatus = database.deviceStatusDao.insert(deviceStatus(recentTime))

        repository.trimForVacuum()

        assertThat(database.apsResultDao.findById(oldResult)).isNull()
        assertThat(database.deviceStatusDao.findById(oldStatus)).isNull()
        // The loop reads the last 24 hours of APS results for the dynamic ISF average.
        assertThat(database.apsResultDao.findById(recentResult)).isNotNull()
        assertThat(database.deviceStatusDao.findById(recentStatus)).isNotNull()
    }

    @Test
    fun `a real vacuum of a database that fits deletes nothing`() = runTest {
        val old = database.apsResultDao.insert(apsResult(now - 30.days.inWholeMilliseconds))

        assertThat(repository.vacuumDatabase()).isEqualTo(VacuumOutcome.DONE)
        assertThat(database.apsResultDao.findById(old)).isNotNull()
    }

    /** The text the bundled driver gave in the Crashlytics report (android.database.SQLException there). */
    private fun outOfMemory() = IllegalStateException("Error code: 7, message: out of memory")

    private fun apsResult(timestamp: Long) = APSResult(
        timestamp = timestamp,
        algorithm = APSResult.Algorithm.SMB,
        glucoseStatusJson = null,
        currentTempJson = null,
        iobDataJson = null,
        profileJson = null,
        autosensDataJson = null,
        mealDataJson = null,
        resultJson = "{}"
    )

    private fun deviceStatus(timestamp: Long) = DeviceStatus(timestamp = timestamp, uploaderBattery = 50, isCharging = false)
}
