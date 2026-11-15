package app.aaps.database.dao

import android.content.Context
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.database.di.AppDatabaseBuilder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-memory database of the instrumented tests runs on the same bundled SQLite as the app.
 *
 * It used to run on the device's own SQLite, which on the CI emulator (Android 12) is far older. The
 * graph's variable sensitivity query reads `resultJson` with `json_extract`, and those results can hold
 * `NaN`. The bundled SQLite reads that; the old one failed, and with it the graph phase of every
 * calculation in the instrumented tests, while the app itself worked.
 */
@RunWith(AndroidJUnit4::class)
class InMemoryDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = AppDatabaseBuilder().provideInMemoryAppDatabase(context)

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun readsJsonWithNaNLikeTheApp() = runTest {
        val isNull = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT json_extract('{\"variable_sens\":NaN}', '\$.variable_sens') IS NULL") { statement ->
                statement.step()
                statement.getLong(0)
            }
        }
        Assert.assertEquals(1L, isNull)
    }

    @Test
    fun customExpressionIndexesAreCreated() = runTest {
        val indexes = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'index'") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }
        Assert.assertTrue(indexes.toString(), indexes.contains("index_temporaryBasals_end"))
    }
}
