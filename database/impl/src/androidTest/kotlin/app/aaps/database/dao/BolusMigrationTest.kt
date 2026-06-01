package app.aaps.database.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aaps.database.AppDatabase
import app.aaps.database.di.DatabaseModule
import app.aaps.database.entities.Bolus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the data-transforming migration32to33 (which rebuilds the boluses table — create new,
 * copy rows, drop old — to add the insulin configuration columns) under the bundled SQLite driver.
 *
 * The other migration tests run on an empty schema, so the copy step touches zero rows. Here a bolus
 * is inserted at the v32 schema BEFORE the rebuild, then the DB is reopened through the production
 * driver so the rebuild + copy run under it; the row must survive and be readable through the DAO.
 *
 * Note: v22 is the earliest exported schema, so migrations 20->21 and 21->22 cannot be seeded/tested
 * with MigrationTestHelper.
 */
@RunWith(AndroidJUnit4::class)
class BolusMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun migration32to33_preservesBolusRowUnderBundledDriver() = runTest {
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
        // Seed a v32 DB and insert a bolus before migration32to33 rebuilds the table.
        helper.createDatabase(TEST_DB_NAME, 32).use { supportDb ->
            supportDb.execSQL(
                "INSERT INTO boluses (version, dateCreated, isValid, timestamp, utcOffset, amount, type, isBasalInsulin) " +
                    "VALUES (1, 0, 1, $BOLUS_TIMESTAMP, 0, $BOLUS_AMOUNT, 'NORMAL', 0)"
            )
        }
        // Reopen through the production driver so 32->33 (rebuild + copy) and 33->34 run under it.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*DatabaseModule().migrations)
            .build()
        try {
            val bolus = db.bolusDao.getLastBolusRecord()
            Assert.assertNotNull("Bolus row was lost during the migration32to33 table rebuild", bolus)
            Assert.assertEquals(BOLUS_AMOUNT, bolus!!.amount, 0.0001)
            Assert.assertEquals(Bolus.Type.NORMAL, bolus.type)
            Assert.assertEquals(BOLUS_TIMESTAMP, bolus.timestamp)
        } finally {
            db.close()
        }
    }

    companion object {

        private const val TEST_DB_NAME = "bolusMigrationTest.db"
        private const val BOLUS_TIMESTAMP = 1_700_000_000_000L
        private const val BOLUS_AMOUNT = 1.5
    }
}
