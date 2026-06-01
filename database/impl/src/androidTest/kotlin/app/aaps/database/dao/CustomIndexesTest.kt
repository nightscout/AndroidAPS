package app.aaps.database.dao

import android.content.Context
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.database.di.DatabaseModule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the custom expression indexes (e.g. `index_temporaryBasals_end` on `timestamp + duration`)
 * that Room cannot declare and that are created in [DatabaseModule]'s open callback. With the bundled
 * SQLite driver, Room delivers an SQLiteConnection (not a SupportSQLiteConnection), so the callback
 * MUST override the connection overload of onOpen — if it doesn't fire, these indexes silently vanish
 * and range queries like getTemporaryBasalActiveAt degrade to full table scans.
 *
 * Builds the database through the real [DatabaseModule] path (bundled driver + callback), so it
 * actually verifies the production wiring rather than a bare test builder. Instrumented because the
 * bundled driver's native library does not load on the host JVM (Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class CustomIndexesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = DatabaseModule().provideAppDatabase(context, TEST_DB_NAME)

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun customExpressionIndexes_areCreatedUnderBundledDriver() = runTest {
        // First connection use forces the DB open and fires the onOpen callback that creates them.
        val indexes = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'index'") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }
        listOf(
            "index_temporaryBasals_end",
            "index_extendedBoluses_end",
            "index_temporaryTargets_end",
            "index_carbs_end",
            "index_runningModes_end"
        ).forEach { expected ->
            Assert.assertTrue("Missing custom index `$expected`. Present indexes: $indexes", indexes.contains(expected))
        }
    }

    companion object {

        private const val TEST_DB_NAME = "customIndexesTest.db"
    }
}
