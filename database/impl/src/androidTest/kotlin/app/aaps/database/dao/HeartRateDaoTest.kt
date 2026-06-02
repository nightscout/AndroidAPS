package app.aaps.database.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.useReaderConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aaps.database.AppDatabase
import app.aaps.database.di.DatabaseModule
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.TABLE_HEART_RATE
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeartRateDaoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun createDatabase() =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun getDbObjects(supportDb: SupportSQLiteDatabase, type: String): Set<String> {
        val names = mutableSetOf<String>()
        supportDb.query("SELECT name FROM sqlite_master WHERE type = '$type'").use { c ->
            while (c.moveToNext()) names.add(c.getString(0))
        }
        return names
    }

    private fun getTableNames(db: SupportSQLiteDatabase) = getDbObjects(db, "table")

    private fun insertAndFind(database: AppDatabase) {
        val hr1 = createHeartRate()
        val id = database.heartRateDao.insert(hr1)
        val hr2 = database.heartRateDao.findById(id)
        Assert.assertTrue(hr1.contentEqualsTo(hr2!!))
    }

    private fun HeartRate.contentEqualsTo(other: HeartRate): Boolean {
        return this === other || (
            duration == other.duration &&
                timestamp == other.timestamp &&
                beatsPerMinute == other.beatsPerMinute &&
                isValid == other.isValid)
    }

    @Test
    fun new_insertAndFind() {
        createDatabase().also { db ->
            insertAndFind(db)
            db.close()
        }
    }

    @Test
    fun migrate_createsTableAndIndices() = runTest {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )
        val startVersion = 22
        // Seed a DB at the old schema version (still missing the heartRate table)...
        val supportDb = helper.createDatabase(TEST_DB_NAME, startVersion)
        Assert.assertFalse(getTableNames(supportDb).contains(TABLE_HEART_RATE))
        // ...then reopen through the production driver so the migration chain runs under the bundled driver.
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*DatabaseModule().migrations)
            .build().also { db ->
                val objects = db.useReaderConnection { connection ->
                    connection.usePrepared("SELECT type, name FROM sqlite_master") { statement ->
                        buildList { while (statement.step()) add(statement.getText(0) to statement.getText(1)) }
                    }
                }
                Assert.assertTrue(objects.contains("table" to TABLE_HEART_RATE))
                Assert.assertTrue(objects.contains("index" to "index_heartRate_timestamp"))
                db.close()
            }
    }

    @Test
    fun migrate_insertAndFind() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )
        // Create the database for version 22 (that's missing the heartRate table).
        // helper.createDatabase removes the db  file if it already exists.
        val supportDb = helper.createDatabase(TEST_DB_NAME, 22)
        Assert.assertFalse(getTableNames(supportDb).contains(TABLE_HEART_RATE))
        // Room.databaseBuilder will use the previously created db file that has version 22.
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, TEST_DB_NAME)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*DatabaseModule().migrations)
            .build().also { db ->
                insertAndFind(db)
                db.close()
            }
    }

    @Test
    fun getFromTime() = runTest {
        createDatabase().also { db ->
            val dao = db.heartRateDao
            val timestamp = System.currentTimeMillis()
            val hr1 = createHeartRate(timestamp = timestamp, beatsPerMinute = 80.0)
            val hr2 = createHeartRate(timestamp = timestamp + 1, beatsPerMinute = 150.0)
            dao.insertNewEntry(hr1)
            dao.insertNewEntry(hr2)

            Assert.assertEquals(listOf(hr1, hr2), dao.getFromTime(timestamp))
            Assert.assertEquals(listOf(hr2), dao.getFromTime(timestamp + 1))
            Assert.assertTrue(dao.getFromTime(timestamp + 2).isEmpty())
            db.close()
        }
    }

    @Test
    fun getFromTimeToTime() = runTest {
        createDatabase().also { db ->
            val dao = db.heartRateDao
            val timestamp = System.currentTimeMillis()
            val hr1 = createHeartRate(timestamp = timestamp, beatsPerMinute = 80.0)
            val hr2 = createHeartRate(timestamp = timestamp + 1, beatsPerMinute = 150.0)
            val hr3 = createHeartRate(timestamp = timestamp + 2, beatsPerMinute = 160.0)
            dao.insertNewEntry(hr1)
            dao.insertNewEntry(hr2)
            dao.insertNewEntry(hr3)

            Assert.assertEquals(listOf(hr1, hr2, hr3), dao.getFromTimeToTime(timestamp, timestamp + 2))
            Assert.assertEquals(listOf(hr1, hr2), dao.getFromTimeToTime(timestamp, timestamp + 1))
            Assert.assertEquals(listOf(hr2), dao.getFromTimeToTime(timestamp + 1, timestamp + 1))
            Assert.assertTrue(dao.getFromTimeToTime(timestamp + 3, timestamp + 10).isEmpty())
            db.close()
        }
    }

    companion object {

        private const val TEST_DB_NAME = "testDatabase"

        fun createHeartRate(timestamp: Long? = null, beatsPerMinute: Double = 80.0) =
            HeartRate(
                timestamp = timestamp ?: System.currentTimeMillis(),
                duration = 60_0000L,
                beatsPerMinute = beatsPerMinute,
                device = "T",
            )

    }
}