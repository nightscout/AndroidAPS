package app.aaps.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Checks which index SQLite uses for the queries that run all the time.
 *
 * Almost every query has `referenceId IS NULL`, which keeps the current version of an entry and drops
 * its history. SQLite sees an equality on an indexed column and takes the `referenceId` index. But
 * NULL is the value of nearly every row, so it then reads the whole table and sorts it. On a real
 * database with 14,000 temporary basals `getTemporaryBasalActiveAt` read all of them on every call,
 * and that one query was most of the database load after every BG on a phone.
 *
 * The queries write `+referenceId IS NULL`. The `+` keeps the condition but stops SQLite from using an
 * index for it, so it takes the timestamp index instead. The "active at" queries also write
 * `+timestamp <= ...`, so they take the `(timestamp + duration)` index (`index_*_end`): only the
 * entries that end after the time are read, which is a few for a recent time - also when nothing runs.
 * Through the timestamp index, a time when nothing runs would read every older entry.
 *
 * The SQL comes from the generated DAO code, recorded by a wrapped driver, so this checks the real
 * queries and not a copy of them. The plan does not depend on the data, so an empty database shows it.
 */
class QueryPlanTest {

    private val directory = createTempDirectory("aaps-plan-test")
    private val driver = PlanRecordingDriver(BundledSQLiteDriver())
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db", driver)

    @AfterTest
    fun tearDown() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `active at queries use the end index`() = runTest {
        assertPlan("index_temporaryBasals_end") { database.temporaryBasalDao.getTemporaryBasalActiveAt(NOW) }
        assertPlan("index_extendedBoluses_end") { database.extendedBolusDao.getExtendedBolusActiveAt(NOW) }
        assertPlan("index_temporaryTargets_end") { database.temporaryTargetDao.getTemporaryTargetActiveAt(NOW) }
        assertPlan("index_runningModes_end") { database.runningModeDao.getTemporaryRunningModeActiveAt(NOW) }
        assertPlan("index_carbs_end") { database.carbsDao.getCarbsFromTimeExpandable(NOW) }
        assertPlan("index_carbs_end") { database.carbsDao.getCarbsFromTimeToTimeExpandable(NOW - DAY, NOW) }
    }

    /**
     * The `Legacy` versions, used by the pump sync transactions. A test of their own, because today they
     * have the same SQL as the ones above: the connection would reuse that prepared statement and the
     * driver would see nothing. Each test gets a new database.
     */
    @Test
    fun `legacy active at queries use the end index`() = runTest {
        assertPlan("index_temporaryBasals_end") { database.temporaryBasalDao.getTemporaryBasalActiveAtLegacy(NOW) }
        assertPlan("index_extendedBoluses_end") { database.extendedBolusDao.getExtendedBolusActiveAtLegacy(NOW) }
        assertPlan("index_temporaryTargets_end") { database.temporaryTargetDao.getTemporaryTargetActiveAtLegacy(NOW) }
    }

    @Test
    fun `newest and from time queries use the timestamp index`() = runTest {
        assertPlan("index_glucoseValues_timestamp") { database.glucoseValueDao.getLast() }
        assertPlan("index_glucoseValues_timestamp") { database.glucoseValueDao.compatGetBgReadingsDataFromTime(NOW - DAY) }
        assertPlan("index_glucoseValues_timestamp") { database.glucoseValueDao.compatGetBgReadingsDataFromTime(NOW - DAY, NOW) }
        assertPlan("index_boluses_timestamp") { database.bolusDao.getLastBolusRecord() }
        assertPlan("index_boluses_timestamp") { database.bolusDao.getLastBolusRecordOfType(Bolus.Type.NORMAL) }
        assertPlan("index_boluses_timestamp") { database.bolusDao.getOldestBolusRecord() }
        assertPlan("index_boluses_timestamp") { database.bolusDao.getBolusesFromTime(NOW - DAY) }
        assertPlan("index_boluses_timestamp") { database.bolusDao.getBolusesFromTime(NOW - DAY, NOW) }
        assertPlan("index_boluses_timestamp") { database.bolusDao.getBolusesIncludingInvalidFromTime(NOW - DAY) }
        assertPlan("index_carbs_timestamp") { database.carbsDao.getCarbsFromTime(NOW - DAY) }
        assertPlan("index_carbs_timestamp") { database.carbsDao.getCarbsIncludingInvalidFromTime(NOW - DAY) }
        assertPlan("index_temporaryBasals_timestamp") { database.temporaryBasalDao.getTemporaryBasalDataFromTime(NOW - DAY) }
        assertPlan("index_temporaryBasals_timestamp") { database.temporaryBasalDao.getTemporaryBasalDataIncludingInvalidFromTime(NOW - DAY) }
        assertPlan("index_bolusCalculatorResults_timestamp") { database.bolusCalculatorResultDao.getBolusCalculatorResultsFromTime(NOW - DAY) }
        assertPlan("index_bolusCalculatorResults_timestamp") { database.bolusCalculatorResultDao.getBolusCalculatorResultsIncludingInvalidFromTime(NOW - DAY) }
    }

    /** Searched by pump ids: the index of the id where one exists, never the referenceId one. */
    @Test
    fun `pump id queries do not use the referenceId index`() = runTest {
        assertPlan("index_temporaryBasals_temporaryId") { database.temporaryBasalDao.findByPumpTempIds(1, PUMP_TYPE, SERIAL) }
        // No temporaryId index on boluses and no pumpId index on carbs: a scan of the table, which
        // still reads less than the referenceId index does for nearly every row.
        assertPlan(null) { database.bolusDao.findByPumpTempIds(1, PUMP_TYPE, SERIAL) }
        assertPlan(null) { database.carbsDao.findByPumpIds(1, PUMP_TYPE, SERIAL) }
    }

    /** Runs [query] and checks the plan of every DAO query it prepared. */
    private suspend fun assertPlan(expectedIndex: String?, query: suspend () -> Unit) {
        driver.plans.clear()
        query()
        val plans = driver.plans.filter { (sql, _) -> sql.contains("referenceId IS NULL") }
        assertWithMessage("no DAO query was recorded").that(plans).isNotEmpty()
        for ((sql, plan) in plans) {
            assertWithMessage(sql).that(plan).doesNotContain("_referenceId")
            // \b: index_temporaryBasals_end must not match index_temporaryBasals_endId
            if (expectedIndex != null) assertWithMessage(sql).that(plan).containsMatch("INDEX $expectedIndex\\b")
        }
    }

    /** Asks SQLite for the plan of every SELECT the DAOs prepare, and keeps it with the SQL. */
    private class PlanRecordingDriver(private val driver: SQLiteDriver) : SQLiteDriver by driver {

        val plans = CopyOnWriteArrayList<Pair<String, String>>()

        override fun open(fileName: String): SQLiteConnection = RecordingConnection(driver.open(fileName))

        private inner class RecordingConnection(private val connection: SQLiteConnection) : SQLiteConnection by connection {

            override fun prepare(sql: String): SQLiteStatement {
                if (sql.trimStart().startsWith("SELECT", ignoreCase = true)) {
                    // Parameters that are not bound are NULL, which does not change the plan
                    val plan = connection.prepare("EXPLAIN QUERY PLAN $sql").use { statement ->
                        buildList { while (statement.step()) add(statement.getText(3)) }.joinToString(" | ")
                    }
                    plans.add(sql to plan)
                }
                return connection.prepare(sql)
            }
        }
    }

    private companion object {

        const val NOW = 1_800_000_000_000L
        const val DAY = 24 * 60 * 60 * 1000L
        const val SERIAL = "S"
        val PUMP_TYPE = InterfaceIDs.PumpType.entries.first()
    }
}
