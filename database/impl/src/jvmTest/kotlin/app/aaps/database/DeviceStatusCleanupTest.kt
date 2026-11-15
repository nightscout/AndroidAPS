package app.aaps.database

import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.APSResult
import app.aaps.database.entities.DeviceStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Device statuses have their own short limit in [AppRepository.cleanupDatabase], against a real
 * database file.
 *
 * They are only queued for the Nightscout upload, but they were kept six months like everything else.
 * Together with the APS results that made a database of 1.6 GB, and its VACUUM failed with "out of
 * memory" on every start. The APS results must keep the normal limit: they will be used later.
 */
class DeviceStatusCleanupTest {

    private val directory = createTempDirectory("aaps-cleanup-test")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")
    private val repository = AppRepository { database }

    @AfterTest
    fun tearDown() {
        repository.close()
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `device statuses keep only a few days while other records keep the normal limit`() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val keep = AppRepository.DEVICE_STATUS_KEEP_DAYS.days.inWholeMilliseconds
        val oneDay = 1.days.inWholeMilliseconds
        val recentStatus = database.deviceStatusDao.insert(deviceStatus(now - keep + oneDay))
        val oldStatus = database.deviceStatusDao.insert(deviceStatus(now - keep - oneDay))
        val oldResult = database.apsResultDao.insert(apsResult(now - keep - oneDay))

        repository.cleanupDatabase(than = now - (6 * 31).days.inWholeMilliseconds, deleteTrackedChanges = false)

        assertThat(database.deviceStatusDao.findById(recentStatus)).isNotNull()
        assertThat(database.deviceStatusDao.findById(oldStatus)).isNull()
        // An APS result of the same age stays: it falls under the six months of everything else.
        assertThat(database.apsResultDao.findById(oldResult)).isNotNull()
    }

    private fun deviceStatus(timestamp: Long) = DeviceStatus(timestamp = timestamp, uploaderBattery = 50, isCharging = false)

    private fun apsResult(timestamp: Long) = APSResult(
        timestamp = timestamp,
        algorithm = APSResult.Algorithm.SMB,
        glucoseStatusJson = null,
        currentTempJson = null,
        iobDataJson = null,
        profileJson = null,
        autosensDataJson = null,
        mealDataJson = null,
        resultJson = "{}"
    )
}
