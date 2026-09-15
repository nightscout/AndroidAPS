package app.aaps.ios.shell

import app.aaps.database.di.IosAppDatabaseBuilder
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.transactions.CgmSourceTransaction
import kotlinx.coroutines.runBlocking

/**
 * Opens the real AAPS database on iOS and puts a reading through it.
 *
 * Room generating code for Kotlin/Native only shows that the generator ran. It does not show that
 * the generated code opens a file, applies the schema and answers a query, and those are the parts
 * that fail first on a new platform. So this writes and reads back rather than inspecting anything.
 *
 * The write goes through [CgmSourceTransaction], the same transaction the CGM sources use, so the
 * path exercised here is the one the app would actually take.
 */
internal object DatabaseProbe {

    private const val FILE_NAME = "aaps-ios-probe.db"

    /** One line per step, or the failure. */
    fun run(): String = try {
        val repository = IosAppDatabaseBuilder().provideAppRepository(FILE_NAME)
        repository.use {
            runBlocking {
                val reading = GlucoseValue(
                    timestamp = 1_700_000_000_000L,
                    raw = null,
                    value = 123.0,
                    trendArrow = GlucoseValue.TrendArrow.FLAT,
                    noise = null,
                    sourceSensor = GlucoseValue.SourceSensor.UNKNOWN
                )

                it.runTransactionSuspend(CgmSourceTransaction(listOf(reading), emptyList(), null))
                val stored = it.getLastGlucoseValue()

                listOf(
                    "opened: $FILE_NAME",
                    "wrote: 1 reading",
                    "read back: " + (stored?.value?.toString() ?: "NOTHING"),
                    "id assigned: " + ((stored?.id ?: 0L) > 0L),
                    "round trip: " + (stored?.value == 123.0)
                ).joinToString("\n")
            }
        }
    } catch (e: Throwable) {
        "DATABASE FAILED: ${e::class.simpleName}: ${e.message}"
    }
}
