package app.aaps.pump.aaruy.upload

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.keys.AaruyBooleanPreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Sends the pump log to the server of the vendor, as the vendor app did, but only while the user has
 * [AaruyBooleanPreferenceKey.UploadHistory] on. It is off by default: the log is health data, tied to the serial number
 * of the pump. Nothing here waits for the pump or holds a command back.
 */
@SingleIn(AppScope::class)
@Inject
class AaruyHistoryUpload(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val historyUnitInfoDao: HistoryUnitInfoDao,
) {

    companion object {

        /** The vendor app waited this long between two records. */
        internal val RECORD_GAP = 7.seconds
        internal val ROUND_PERIOD = 15.minutes
        private const val BATCH = 100
        private const val REQUEST_TIMEOUT_MS = 30_000L
    }

    /** Made at the first upload, so nothing connects while the setting is off. A test sets its own. */
    internal var cloud: AaruyCloud? = null

    private fun cloud(): AaruyCloud =
        cloud ?: AaruyCloud(HttpClient(aaruyHttpEngine) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }, aapsLogger)
            .also { cloud = it }

    /** A round at once and then every [ROUND_PERIOD] while the setting is on. Turning it off ends a round at once. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            preferences.observe(AaruyBooleanPreferenceKey.UploadHistory).collectLatest { on ->
                while (on) {
                    try {
                        val sent = uploadPending()
                        if (sent > 0) aapsLogger.debug(LTag.PUMPCOMM, "Upload: $sent entries sent")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.PUMPCOMM, "Upload failed", e)
                    }
                    delay(ROUND_PERIOD)
                }
            }
        }
    }

    /**
     * Sends the entries not sent yet, oldest first, [RECORD_GAP] apart. Stops at the first one the server does not take,
     * and the next round starts with it.
     * @return how many were sent
     */
    internal suspend fun uploadPending(): Int {
        var sent = 0
        while (true) {
            val entries = historyUnitInfoDao.notUploaded(BATCH)
            if (entries.isEmpty()) return sent
            for (entry in entries) {
                if (sent > 0) delay(RECORD_GAP)
                val offset = indexOffset(entry.blueName) ?: return sent
                val productCode = AaruyType.fromValue(preferences.get(AaruyIntKey.PumpType)).productCode
                val record = entry.toCloudRecord(entry.id + offset, productCode)
                if (!cloud().send(record)) {
                    aapsLogger.warn(LTag.PUMPCOMM, "Upload: entry ${entry.id} (kind ${record.kind}) was not taken. It is sent again in the next round.")
                    return sent
                }
                historyUnitInfoDao.markUploaded(entry.id)
                sent++
            }
        }
    }

    /**
     * The vendor app gave a record the id of its entry plus the last index the server had of the pump when the app first
     * sent. So an app that starts again with an empty log goes on after its old records. Asked again for another pump.
     * @return null when the server could not be asked
     */
    private suspend fun indexOffset(serial: String): Int? {
        val known = preferences.get(AaruyStringKey.UploadSerial)
        if (known.isNotEmpty() && known == serial) return preferences.get(AaruyIntKey.UploadIndexOffset)
        val last = cloud().lastIndex(serial)
        if (last == null) {
            aapsLogger.warn(LTag.PUMPCOMM, "Upload: the last record of $serial is not known. Again in the next round.")
            return null
        }
        preferences.put(AaruyIntKey.UploadIndexOffset, last)
        preferences.put(AaruyStringKey.UploadSerial, serial)
        return last
    }
}
