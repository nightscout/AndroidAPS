package app.aaps.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.di.databaseMigrations
import app.aaps.database.entities.TABLE_BOLUSES
import app.aaps.database.entities.TABLE_EFFECTIVE_PROFILE_SWITCHES
import app.aaps.database.entities.TABLE_PROFILE_SWITCHES
import app.aaps.database.entities.embedments.InsulinConfiguration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The database of a 3.x AAPSClient through every migration, from 32 to the current version, on a real file (#5625).
 *
 * `InsulinConfigMigration33Test` runs the one migration on three tables made by hand. This builds the whole v32
 * schema from the exported `32.json`, as 3.4 left it, opens it with Room and the full list of migrations, and
 * lets Room check the result against the entities. Then it does what `MainApp.dataMigrations` does with the
 * database: fill the missing peaks first, then stamp the rows that have nothing.
 */
class InsulinUpgradeChainTest {

    private val directory = createTempDirectory("aaps-upgrade-chain-test")
    private val path = "$directory/aaps.db"
    private var database: AppDatabase? = null

    @AfterTest
    fun tearDown() {
        database?.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a 3x client database keeps the DIA through every migration`() = runTest {
        createV32Database {
            // What a 3.4 AAPSClient stored for what it got from Nightscout: the profile's DIA, peak 0, no label
            execSQL(
                "INSERT INTO `$TABLE_PROFILE_SWITCHES` (version, dateCreated, isValid, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, " +
                    "targetBlocks, glucoseUnit, profileName, timeshift, percentage, duration, nightscoutId, insulinLabel, insulinEndTime, peak) " +
                    "VALUES (1, 0, 1, $TIME, 0, '[]', '[]', '[]', '[]', 'MGDL', 'Adult', 0, 100, 0, 'ps-from-ns', '', $DIA_5H, 0)"
            )
            execSQL(
                "INSERT INTO `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (version, dateCreated, isValid, timestamp, utcOffset, basalBlocks, isfBlocks, " +
                    "icBlocks, targetBlocks, glucoseUnit, originalProfileName, originalCustomizedName, originalTimeshift, originalPercentage, " +
                    "originalDuration, originalEnd, nightscoutId, insulinLabel, insulinEndTime, peak) " +
                    "VALUES (1, 0, 1, $TIME, 0, '[]', '[]', '[]', '[]', 'MGDL', 'Adult', 'Adult', 0, 100, 0, 0, 'eps-from-ns', '', $DIA_5H, 0)"
            )
            // v3 never stamped a bolus
            execSQL(
                "INSERT INTO `$TABLE_BOLUSES` (version, dateCreated, isValid, timestamp, utcOffset, amount, type, isBasalInsulin, nightscoutId) " +
                    "VALUES (1, 0, 1, $TIME, 0, 1.5, 'NORMAL', 0, 'bolus-from-ns')"
            )
        }

        // The first query opens the file: Room runs every migration from 32 and then checks the schema.
        val db = openWithAllMigrations().also { database = it }
        val repository = AppRepository { db }

        // After the migrations the DIA is still there, and only the peak is missing.
        val profileSwitch = db.profileSwitchDao.getAllProfileSwitches().single()
        assertThat(profileSwitch.insulinConfiguration).isEqualTo(InsulinConfiguration("", DIA_5H, -1, 1.0))
        // The Nightscout id too, which is what keeps the sync from loading these records again.
        assertThat(profileSwitch.interfaceIDs.nightscoutId).isEqualTo("ps-from-ns")
        assertThat(db.effectiveProfileSwitchDao.getAllEffectiveProfileSwitches().single().insulinConfiguration).isEqualTo(InsulinConfiguration("", DIA_5H, -1, 1.0))
        assertThat(db.bolusDao.getAllBoluses().single().insulinConfiguration).isEqualTo(InsulinConfiguration("", -1, -1, 1.0))

        // MainApp.dataMigrations: the missing peaks first, from the v3 insulin selection...
        assertThat(repository.fillMissingProfileSwitchInsulinPeak(NOVORAPID, PEAK_75M)).isEqualTo(1)
        assertThat(repository.fillMissingEffectiveProfileSwitchInsulinPeak(NOVORAPID, PEAK_75M)).isEqualTo(1)
        // ...then the plan reads the running record, which is now a whole insulin, and stamps what has nothing with it.
        assertThat(repository.bulkMigrateProfileSwitchInsulinConfig(NOVORAPID, DIA_5H, PEAK_75M, 1.0)).isEqualTo(0)
        assertThat(repository.bulkMigrateEffectiveProfileSwitchInsulinConfig(NOVORAPID, DIA_5H, PEAK_75M, 1.0)).isEqualTo(0)
        assertThat(repository.bulkMigrateBolusInsulinConfig(NOVORAPID, DIA_5H, PEAK_75M, 1.0)).isEqualTo(1)

        val expected = InsulinConfiguration(NOVORAPID, DIA_5H, PEAK_75M, 1.0)
        assertThat(db.profileSwitchDao.getAllProfileSwitches().single().insulinConfiguration).isEqualTo(expected)
        assertThat(db.effectiveProfileSwitchDao.getAllEffectiveProfileSwitches().single().insulinConfiguration).isEqualTo(expected)
        assertThat(db.bolusDao.getAllBoluses().single().insulinConfiguration).isEqualTo(expected)
    }

    /** The complete v32 schema from the file Room exported for it, with [rows] in it, at user_version 32. */
    private fun createV32Database(rows: SQLiteConnection.() -> Unit) {
        val schema = File(SCHEMA_32)
        check(schema.exists()) { "No exported schema at ${schema.absolutePath}" }
        val database = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
        check(database.getValue("version").jsonPrimitive.content == "32")
        BundledSQLiteDriver().open(path).use { connection ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                fun sql(statement: String) = statement.replace("\${TABLE_NAME}", table)
                connection.execSQL(sql(entity.jsonObject.getValue("createSql").jsonPrimitive.content))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(sql(index.jsonObject.getValue("createSql").jsonPrimitive.content))
                }
            }
            database.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.rows()
            connection.execSQL("PRAGMA user_version = 32")
        }
    }

    private fun openWithAllMigrations(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*databaseMigrations)
            .build()

    private companion object {

        /** Room's export of the 3.4 schema, from this module's directory, where Gradle runs the tests. */
        const val SCHEMA_32 = "src/androidDeviceTest/assets/app.aaps.database.AppDatabase/32.json"

        const val TIME = 1_700_000_000_000L
        const val NOVORAPID = "Novorapid"
        const val DIA_5H = 5L * 3600 * 1000
        const val PEAK_75M = 75L * 60 * 1000
    }
}
