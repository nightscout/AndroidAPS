package app.aaps.database.impl

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aaps.database.entities.StepsCount
import app.aaps.database.entities.TABLE_STEPS_COUNT
import app.aaps.database.impl.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepsCountDaoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun createDatabase() =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    private fun getDbObjects(supportDb: SupportSQLiteDatabase, type: String): Set<String> {
        val names = mutableSetOf<String>()
        supportDb.query("SELECT name FROM sqlite_master WHERE type = '$type'").use { c ->
            while (c.moveToNext()) names.add(c.getString(0))
        }
        return names
    }

    private fun getTableNames(db: SupportSQLiteDatabase) = getDbObjects(db, "table")
    private fun getIndexNames(db: SupportSQLiteDatabase) = getDbObjects(db, "index")

    private fun insertAndFind(database: AppDatabase) {
        val sc1 = createStepsCount()
        val id = database.stepsCountDao.insert(sc1)
        val sc2 = database.stepsCountDao.findById(id)
        assertTrue(sc1.contentEqualsTo(sc2!!))
    }

    @Test
    fun new_insertAndFind() {
        createDatabase().use { db -> insertAndFind(db) }
    }

    @Test
    fun migrate_createsTableAndIndices() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )
        val startVersion = 24
        val supportDb = helper.createDatabase(TEST_DB_NAME, startVersion)
        assertFalse(getTableNames(supportDb).contains(TABLE_STEPS_COUNT))
        DatabaseModule().migrations.filter { m -> m.startVersion >= startVersion }.forEach { m -> m.migrate(supportDb) }
        assertTrue(getTableNames(supportDb).contains(TABLE_STEPS_COUNT))
        assertTrue(getIndexNames(supportDb).contains("index_stepsCount_id"))
        assertTrue(getIndexNames(supportDb).contains("index_stepsCount_timestamp"))
    }

    @Test
    fun migrate_insertAndFind() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )
        // Create the database for version 24 (that's missing the stepsCount table).
        // helper.createDatabase removes the db  file if it already exists.
        val supportDb = helper.createDatabase(TEST_DB_NAME, 22)
        assertFalse(getTableNames(supportDb).contains(TABLE_STEPS_COUNT))
        // Room.databaseBuilder will use the previously created db file that has version 22.
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, TEST_DB_NAME)
            .addMigrations(*DatabaseModule().migrations)
            .build().use { db -> insertAndFind(db) }
    }

    @Test
    fun getFromTime() {
        createDatabase().use { db ->
            val dao = db.stepsCountDao
            val timestamp = System.currentTimeMillis()
            val sc1 = createStepsCount(timestamp = timestamp, steps5Min = 80)
            val sc2 = createStepsCount(timestamp = timestamp + 1, steps5Min = 150)
            dao.insertNewEntry(sc1)
            dao.insertNewEntry(sc2)

            assertEquals(listOf(sc1, sc2), dao.getFromTime(timestamp).blockingGet())
            assertEquals(listOf(sc2), dao.getFromTime(timestamp + 1).blockingGet())
            assertTrue(dao.getFromTime(timestamp + 2).blockingGet().isEmpty())
        }
    }

    @Test
    fun getFromTimeToTime() {
        createDatabase().use { db ->
            val dao = db.stepsCountDao
            val timestamp = System.currentTimeMillis()
            val hr1 = createStepsCount(timestamp = timestamp, steps5Min = 80)
            val hr2 = createStepsCount(timestamp = timestamp + 1, steps5Min = 150)
            val hr3 = createStepsCount(timestamp = timestamp + 2, steps5Min = 160)
            dao.insertNewEntry(hr1)
            dao.insertNewEntry(hr2)
            dao.insertNewEntry(hr3)

            assertEquals(listOf(hr1, hr2, hr3), dao.getFromTimeToTime(timestamp, timestamp + 2).blockingGet())
            assertEquals(listOf(hr1, hr2), dao.getFromTimeToTime(timestamp, timestamp + 1).blockingGet())
            assertEquals(listOf(hr2), dao.getFromTimeToTime(timestamp + 1, timestamp + 1).blockingGet())
            assertTrue(dao.getFromTimeToTime(timestamp + 3, timestamp + 10).blockingGet().isEmpty())
        }
    }

    companion object {

        private const val TEST_DB_NAME = "testDatabase"

        fun createStepsCount(timestamp: Long? = null, steps5Min: Int = 80) =
            StepsCount(
                timestamp = timestamp ?: System.currentTimeMillis(),
                duration = 60_0000L,
                steps5min = steps5Min,
                steps10min = steps5Min,
                steps15min = steps5Min,
                steps30min = steps5Min,
                steps60min = steps5Min,
                steps180min = steps5Min,
                device = "T"
            )

    }
}
