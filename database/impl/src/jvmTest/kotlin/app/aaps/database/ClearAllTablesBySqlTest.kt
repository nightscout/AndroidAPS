package app.aaps.database

import androidx.room.useReaderConnection
import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.StepsCount
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The reset databases action, against a real database file.
 *
 * `clearAllTablesBySql` is the version every target except Android uses, so this is the only place
 * the SQL itself is checked. Desktop is the target that runs it here, and iOS runs the very same
 * common code with the same bundled SQLite - what passes here is what happens on a phone.
 */
class ClearAllTablesBySqlTest {

    private val directory = createTempDirectory("aaps-clear-test")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")

    @AfterTest
    fun tearDown() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `clearing empties every table`() = runTest {
        database.heartRateDao.insert(HeartRate(timestamp = 1_000L, duration = 60_000L, beatsPerMinute = 80.0, device = "T"))
        database.stepsCountDao.insert(stepsCount())
        assertThat(database.heartRateDao.getFromTime(0)).hasSize(1)
        assertThat(database.stepsCountDao.getFromTime(0)).hasSize(1)

        database.clearAllTablesBySql()

        // Both, not just one: the tables come from `sqlite_master` rather than a written list, and a
        // list is exactly what would clear the first table and quietly keep the second.
        assertThat(database.heartRateDao.getFromTime(0)).isEmpty()
        assertThat(database.stepsCountDao.getFromTime(0)).isEmpty()
    }

    @Test
    fun `clearing keeps the tables and the schema themselves`() = runTest {
        database.heartRateDao.insert(HeartRate(timestamp = 1_000L, duration = 60_000L, beatsPerMinute = 80.0, device = "T"))

        database.clearAllTablesBySql()

        // A reset empties the history; it does not drop the database. Room reads its identity out of
        // `room_master_table` on the next open, so emptying that one would leave a database Room no
        // longer recognises - which is why the query skips the `room_` and `sqlite_` names.
        assertThat(tableNames()).contains("heartRate")
        assertThat(rowCount("room_master_table")).isEqualTo(1)

        // And it still works afterwards, rather than only looking right.
        database.heartRateDao.insert(HeartRate(timestamp = 2_000L, duration = 60_000L, beatsPerMinute = 90.0, device = "T"))
        assertThat(database.heartRateDao.getFromTime(0)).hasSize(1)
    }

    @Test
    fun `clearing does not restart the generated ids`() = runTest {
        val first = database.heartRateDao.insert(HeartRate(timestamp = 1_000L, duration = 60_000L, beatsPerMinute = 80.0, device = "T"))

        database.clearAllTablesBySql()
        val second = database.heartRateDao.insert(HeartRate(timestamp = 2_000L, duration = 60_000L, beatsPerMinute = 90.0, device = "T"))

        // Same as Room on Android, and it matters: Nightscout records point at these ids, so a fresh
        // record reusing an old one would be matched to a treatment somebody deleted.
        assertThat(second).isGreaterThan(first)
    }

    private suspend fun tableNames(): List<String> =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_master WHERE type='table'") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }

    private suspend fun rowCount(table: String): Long =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM `$table`") { statement ->
                if (statement.step()) statement.getLong(0) else 0L
            }
        }

    private fun stepsCount() = StepsCount(
        duration = 60_000L,
        timestamp = 1_000L,
        steps5min = 1,
        steps10min = 2,
        steps15min = 3,
        steps30min = 4,
        steps60min = 5,
        steps180min = 6,
        device = "T"
    )
}
