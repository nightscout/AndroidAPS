package app.aaps.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.di.databaseMigrations
import app.aaps.database.entities.TABLE_BOLUSES
import app.aaps.database.entities.TABLE_EFFECTIVE_PROFILE_SWITCHES
import app.aaps.database.entities.TABLE_PROFILE_SWITCHES
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The v3 insulin must survive the 32 to 33 rebuild.
 *
 * v3 stored the insulin on every profile switch record: `ProfileFunctionImpl.buildProfileSwitch` wrote the
 * insulin plugin's label and peak together with `insulinEndTime = pureProfile.dia * 3600 * 1000`, and
 * `ProfileSealed.PS.dia` read the DIA back out of it. Those three columns are the per-record truth about
 * which insulin delivered a dose.
 *
 * The migration used to overwrite all three with `'', -1, -1, 1.0` and leave `MainApp` to reconstruct ONE
 * insulin from the dead insulin-plugin preferences and stamp it over the whole history. The reconstruction
 * keeps only the DIA of the profile that happened to be active, and falls back to the hard-limit maximum of
 * 9 h when it cannot find one. It is one-way, so a wrong value is never revisited. This test is what keeps
 * that from coming back.
 *
 * ## Why here and not in `androidDeviceTest`
 *
 * The SQL is in commonMain and the bundled SQLite is the same engine on every platform, so a raw connection
 * on the JVM runs exactly what a phone runs. `jvmTest` is in the CI command, an instrumented test is not -
 * and `BolusMigrationTest` already showed the gap: it runs this very migration and only checks that the
 * bolus row survives, never what landed in the insulin columns.
 *
 * The tables are created from the exported `32.json` schema rather than through `MigrationTestHelper`, which
 * needs the Android instrumentation.
 */
class InsulinConfigMigration33Test {

    private val directory = createTempDirectory("aaps-insulin-migration-test")
    private val connection: SQLiteConnection = BundledSQLiteDriver().open("$directory/aaps.db")

    @AfterTest
    fun tearDown() {
        connection.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a profile switch keeps the insulin v3 stored on it`() {
        createV32Tables()
        insertProfileSwitch(label = RAPID, endTime = DIA_6H, peak = PEAK_75M)

        migrate32to33()

        assertThat(insulinColumnsOf(TABLE_PROFILE_SWITCHES)).isEqualTo(Insulin(RAPID, DIA_6H, PEAK_75M, 1.0))
    }

    @Test
    fun `an effective profile switch keeps the insulin v3 stored on it`() {
        createV32Tables()
        insertEffectiveProfileSwitch(label = LYUMJEV, endTime = DIA_5H, peak = PEAK_45M)

        migrate32to33()

        // The DIA here is the one the loop actually dosed with. Replacing it with a guess is wrong IOB on
        // every historical record, which is why this asserts the exact value and not just "not the sentinel".
        assertThat(insulinColumnsOf(TABLE_EFFECTIVE_PROFILE_SWITCHES)).isEqualTo(Insulin(LYUMJEV, DIA_5H, PEAK_45M, 1.0))
    }

    @Test
    fun `a row with no insulin at all gets the sentinel`() {
        createV32Tables()
        insertEffectiveProfileSwitch(label = "", endTime = 0, peak = 0)

        migrate32to33()

        // Nothing to carry, so the sentinel stays and `MainApp` stamps this row from the legacy preferences.
        assertThat(insulinColumnsOf(TABLE_EFFECTIVE_PROFILE_SWITCHES)).isEqualTo(SENTINEL)
    }

    @Test
    fun `a row with a DIA but no peak gets the sentinel`() {
        createV32Tables()
        insertProfileSwitch(label = RAPID, endTime = DIA_6H, peak = 0)

        migrate32to33()

        // All three together or none: half a curve computes an IOB that looks plausible and is not.
        assertThat(insulinColumnsOf(TABLE_PROFILE_SWITCHES)).isEqualTo(SENTINEL)
    }

    @Test
    fun `a bolus gets the sentinel because v3 never stamped one`() {
        createV32Tables()
        // NULL columns, which is what every v3 bolus row has: `BS.icfg` is null by default and no v3 code
        // ever assigned it. A NULL compares as neither `> 0`, so it falls to the sentinel on its own.
        connection.execSQL(
            "INSERT INTO `$TABLE_BOLUSES` (version, dateCreated, isValid, timestamp, utcOffset, amount, type, isBasalInsulin) " +
                "VALUES (1, 0, 1, 1700000000000, 0, 1.5, 'NORMAL', 0)"
        )

        migrate32to33()

        assertThat(insulinColumnsOf(TABLE_BOLUSES)).isEqualTo(SENTINEL)
    }

    @Test
    fun `the row itself still survives the rebuild`() {
        createV32Tables()
        insertProfileSwitch(label = RAPID, endTime = DIA_6H, peak = PEAK_75M)

        migrate32to33()

        // The rebuild drops and renames tables, so "the insulin is right" means nothing if the row is gone.
        assertThat(rowCount(TABLE_PROFILE_SWITCHES)).isEqualTo(1)
        assertThat(columnValue(TABLE_PROFILE_SWITCHES, "profileName")).isEqualTo(PROFILE_NAME)
    }

    private fun migrate32to33() =
        databaseMigrations.single { it.startVersion == 32 && it.endVersion == 33 }.migrate(connection)

    private data class Insulin(val label: String, val endTime: Long, val peakTime: Long, val concentration: Double)

    private fun insulinColumnsOf(table: String): Insulin =
        connection.prepare("SELECT insulinLabel, insulinEndTime, insulinPeakTime, concentration FROM `$table`").use { statement ->
            check(statement.step()) { "no row in $table" }
            Insulin(statement.getText(0), statement.getLong(1), statement.getLong(2), statement.getDouble(3))
        }

    private fun rowCount(table: String): Long =
        connection.prepare("SELECT COUNT(*) FROM `$table`").use { statement ->
            if (statement.step()) statement.getLong(0) else 0L
        }

    private fun columnValue(table: String, column: String): String =
        connection.prepare("SELECT `$column` FROM `$table`").use { statement ->
            check(statement.step()) { "no row in $table" }
            statement.getText(0)
        }

    private fun insertProfileSwitch(label: String, endTime: Long, peak: Long) =
        connection.execSQL(
            "INSERT INTO `$TABLE_PROFILE_SWITCHES` (version, dateCreated, isValid, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, " +
                "targetBlocks, glucoseUnit, profileName, timeshift, percentage, duration, insulinLabel, insulinEndTime, peak) " +
                "VALUES (1, 0, 1, 1700000000000, 0, '[]', '[]', '[]', '[]', 'MGDL', '$PROFILE_NAME', 0, 100, 0, '$label', $endTime, $peak)"
        )

    private fun insertEffectiveProfileSwitch(label: String, endTime: Long, peak: Long) =
        connection.execSQL(
            "INSERT INTO `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (version, dateCreated, isValid, timestamp, utcOffset, basalBlocks, isfBlocks, " +
                "icBlocks, targetBlocks, glucoseUnit, originalProfileName, originalCustomizedName, originalTimeshift, originalPercentage, " +
                "originalDuration, originalEnd, insulinLabel, insulinEndTime, peak) " +
                "VALUES (1, 0, 1, 1700000000000, 0, '[]', '[]', '[]', '[]', 'MGDL', '$PROFILE_NAME', '$PROFILE_NAME', 0, 100, 0, 0, '$label', $endTime, $peak)"
        )

    /** The three tables as `32.json` declares them, so the migration reads the columns it really finds. */
    private fun createV32Tables() {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TABLE_BOLUSES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, " +
                "`dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, " +
                "`utcOffset` INTEGER NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `notes` TEXT, `isBasalInsulin` INTEGER NOT NULL, " +
                "`nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, " +
                "`startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT, `insulinEndTime` INTEGER, `peak` INTEGER, " +
                "FOREIGN KEY(`referenceId`) REFERENCES `boluses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, " +
                "`timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `basalBlocks` TEXT NOT NULL, `isfBlocks` TEXT NOT NULL, " +
                "`icBlocks` TEXT NOT NULL, `targetBlocks` TEXT NOT NULL, `glucoseUnit` TEXT NOT NULL, `originalProfileName` TEXT NOT NULL, " +
                "`originalCustomizedName` TEXT NOT NULL, `originalTimeshift` INTEGER NOT NULL, `originalPercentage` INTEGER NOT NULL, " +
                "`originalDuration` INTEGER NOT NULL, `originalEnd` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, " +
                "`pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, " +
                "`insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `peak` INTEGER NOT NULL, " +
                "FOREIGN KEY(`referenceId`) REFERENCES `effectiveProfileSwitches`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TABLE_PROFILE_SWITCHES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, " +
                "`dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, " +
                "`utcOffset` INTEGER NOT NULL, `basalBlocks` TEXT NOT NULL, `isfBlocks` TEXT NOT NULL, `icBlocks` TEXT NOT NULL, " +
                "`targetBlocks` TEXT NOT NULL, `glucoseUnit` TEXT NOT NULL, `profileName` TEXT NOT NULL, `timeshift` INTEGER NOT NULL, " +
                "`percentage` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, " +
                "`pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, " +
                "`insulinEndTime` INTEGER NOT NULL, `peak` INTEGER NOT NULL, " +
                "FOREIGN KEY(`referenceId`) REFERENCES `profileSwitches`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
    }

    private companion object {

        const val PROFILE_NAME = "Adult"
        const val RAPID = "Rapid-Acting Oref"
        const val LYUMJEV = "Lyumjev"
        const val DIA_5H = 5L * 3600 * 1000
        const val DIA_6H = 6L * 3600 * 1000
        const val PEAK_45M = 45L * 60 * 1000
        const val PEAK_75M = 75L * 60 * 1000

        val SENTINEL = Insulin("", -1, -1, 1.0)
    }
}
