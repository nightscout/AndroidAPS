package app.aaps.ios.shell.db

import app.aaps.database.di.IosAppDatabaseBuilder
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.transactions.CgmSourceTransaction
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The AAPS database, opened and used on iOS.
 *
 * Room generating code for Kotlin/Native only shows that the generator ran. These make the
 * generated code do the work: create a file, apply the schema, run transactions and answer
 * queries. Each test uses its own database file so one failure cannot cascade into the next.
 */
class AppDatabaseIosTest {

    private val files = mutableListOf<String>()

    private fun repository(name: String) =
        IosAppDatabaseBuilder().provideAppRepository(name).also { files += name }

    @AfterTest
    fun removeDatabases() {
        // Deleting through the builder's own path logic rather than guessing where the file went.
        files.forEach { IosAppDatabaseBuilder().deleteDatabase(it) }
    }

    private fun reading(timestamp: Long, value: Double) = GlucoseValue(
        timestamp = timestamp,
        raw = null,
        value = value,
        trendArrow = GlucoseValue.TrendArrow.FLAT,
        noise = null,
        sourceSensor = GlucoseValue.SourceSensor.UNKNOWN
    )

    @Test
    fun `a glucose value survives a write and a read`() = runTest {
        repository("test-glucose.db").use { repo ->
            repo.runTransactionSuspend(CgmSourceTransaction(listOf(reading(1_000L, 123.0)), emptyList(), null))

            val stored = repo.getLastGlucoseValue()

            assertNotNull(stored)
            assertEquals(123.0, stored.value)
            // Room assigns the primary key. A zero here would mean the row was never really written.
            assertTrue(stored.id > 0L)
        }
    }

    @Test
    fun `several readings are all stored and come back in order`() = runTest {
        repository("test-many.db").use { repo ->
            repo.runTransactionSuspend(
                CgmSourceTransaction(
                    listOf(reading(1_000L, 100.0), reading(2_000L, 110.0), reading(3_000L, 120.0)),
                    emptyList(),
                    null
                )
            )

            val all = repo.compatGetBgReadingsDataFromTime(0L, ascending = true)

            assertEquals(listOf(100.0, 110.0, 120.0), all.map { it.value })
        }
    }

    @Test
    fun `data is still there after the database is closed and opened again`() = runTest {
        val name = "test-reopen.db"
        repository(name).use { repo ->
            repo.runTransactionSuspend(CgmSourceTransaction(listOf(reading(5_000L, 99.0)), emptyList(), null))
        }

        // A database that only ever worked in memory would pass every test above and fail this one.
        IosAppDatabaseBuilder().provideAppRepository(name).use { reopened ->
            assertEquals(99.0, reopened.getLastGlucoseValue()?.value)
        }
    }

    @Test
    fun `a user entry round trips through the type converters`() = runTest {
        repository("test-userentry.db").use { repo ->
            repo.insert(
                UserEntry(
                    timestamp = 10_000L,
                    action = UserEntry.Action.BOLUS,
                    source = UserEntry.Sources.Automation,
                    note = "from ios",
                    values = listOf(ValueWithUnit.Insulin(1.5))
                )
            )

            val entries = repo.getUserEntryDataFromTime(0L)

            assertEquals(1, entries.size)
            // The interesting part is `values`: a list of a sealed type, stored through the
            // converters that were rewritten off Gson. If serialisation differed on iOS this is
            // where it would show.
            assertEquals("from ios", entries.first().note)
            assertEquals(listOf(ValueWithUnit.Insulin(1.5)), entries.first().values)
        }
    }

    @Test
    fun `two databases do not see each other's rows`() = runTest {
        repository("test-a.db").use { a ->
            a.runTransactionSuspend(CgmSourceTransaction(listOf(reading(1_000L, 55.0)), emptyList(), null))
        }

        repository("test-b.db").use { b ->
            assertEquals(null, b.getLastGlucoseValue())
        }
    }
}
