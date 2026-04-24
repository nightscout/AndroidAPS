package app.aaps.plugins.source.instara

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class InstaraStaleCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences

    // Guard so worker never crashes if injection fails
    private var injectedOk: Boolean = false

    init {
        try {
            (applicationContext as? HasAndroidInjector)
                ?.androidInjector()
                ?.inject(this)
            // Mark injection success only if no exception
            injectedOk = true
        } catch (t: Throwable) {
            injectedOk = false
            Log.e("InstaraStaleCheckWorker", "Injection failed", t)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // If injection failed, do NOT touch lateinit fields; reschedule and exit safely
        if (!injectedOk) {
            Log.e("InstaraStaleCheckWorker", "Skipping run because injection failed; will reschedule.")
            scheduleNext(applicationContext)
            return@withContext Result.success()
        }

        val enabledNow = preferences.get(BooleanKey.InstaraHistoryRequestEnabled)
        val now = System.currentTimeMillis()

        aapsLogger.debug(LTag.CORE, "Instara check START enabled=$enabledNow workId=$id attempt=$runAttemptCount now=$now")

        try {
            if (!enabledNow) {
                // OFF: Do NOT cancel. Just skip this run and reschedule so turning ON later works automatically.
                aapsLogger.debug(LTag.CORE, "Instara check SKIP (disabled by setting) workId=$id")
                return@withContext Result.success()
            }

            // Current device prefix derived from latest stored pumpId
            val devicePrefix: Long = readLatestDevicePrefixFromMeta()
                ?: run {
                    aapsLogger.debug(LTag.CORE, "Instara check: no devicePrefix -> request sgvId=0")
                    sendHistoryRequestBroadcast(idStart = 0L)
                    return@withContext Result.success()
                }

            // (1) If no sgvMark can be found for current device -> request sgvId=0 immediately.
            val meta = readDeviceMeta(devicePrefix)
            if (meta == null) {
                aapsLogger.debug(LTag.CORE, "Instara check: no device meta for device=$devicePrefix -> request sgvId=0")
                sendHistoryRequestBroadcast(idStart = 0L)
                return@withContext Result.success()
            }

            val sgvStart = meta.sgvStart
            val sgvMark = meta.sgvMark
            if (sgvStart <= 0L || sgvMark !in 0..INSTARA_MARK_MAX) {
                aapsLogger.debug(LTag.CORE, "Instara check: invalid device meta for device=$devicePrefix (sgvStart=$sgvStart sgvMark=$sgvMark) -> request sgvId=0")
                sendHistoryRequestBroadcast(idStart = 0L)
                return@withContext Result.success()
            }

            // Bound scan/range so we will NOT scan/request beyond sgvMark for this device.
            val deviceStart = devicePrefix * INSTARA_MARK_FACTOR
            val maxAllowed = deviceStart + sgvMark.toLong()

            val deviceValues = persistenceLayer.getGlucoseValuesByPumpIdRange(SourceSensor.INSTARA, deviceStart, maxAllowed)
            val latestPumpId: Long = deviceValues.lastOrNull()?.ids?.pumpId
                ?: run {
                    aapsLogger.debug(LTag.CORE, "Instara check: no latestPumpId for device=$devicePrefix -> request sgvId=0")
                    sendHistoryRequestBroadcast(idStart = 0L)
                    return@withContext Result.success()
                }

            val scanStart = maxOf(sgvStart, deviceStart)
            val scanEnd = minOf(latestPumpId, maxAllowed)
            val latestRequestable = minOf(latestPumpId, maxAllowed)

            aapsLogger.debug(
                LTag.CORE,
                "Instara check: device=$devicePrefix sgvStart=$sgvStart sgvMark=$sgvMark deviceStart=$deviceStart maxAllowed=$maxAllowed latest=$latestPumpId scanStart=$scanStart scanEnd=$scanEnd"
            )

            if (scanEnd < scanStart) {
                aapsLogger.debug(LTag.CORE, "Instara check: bounded scanEnd < scanStart -> request sgvId=0 (scanStart=$scanStart scanEnd=$scanEnd)")
                sendHistoryRequestBroadcast(idStart = 0L)
                return@withContext Result.success()
            }

            // (2) Search for first missing in bounded range using ONE DB call
            var expected = scanStart
            for (id in deviceValues.asSequence().mapNotNull { it.ids.pumpId }.filter { it in scanStart..scanEnd }) { if (id > expected) break; if (id == expected) expected++ }
            val missing: Long? = if (expected <= scanEnd) expected else null

            if (missing != null) {
                aapsLogger.debug(LTag.CORE, "Instara check: FIRST missing found -> request sgvId=$missing")
                sendHistoryRequestBroadcast(idStart = missing)
                return@withContext Result.success()
            }

            // (3) No gap in bounded range.
            if (scanEnd == maxAllowed) {
                // COMPLETE: Do NOT cancel. Just skip requesting and keep rescheduling.
                aapsLogger.debug(
                    LTag.CORE,
                    "Instara check: COMPLETE device=$devicePrefix received all data in [$scanStart..$scanEnd] (==maxAllowed). No request."
                )
                return@withContext Result.success()
            }

            // (4) Otherwise: staleness check for continuing incremental requests
            val latestTs: Long = deviceValues.maxOfOrNull { it.timestamp }
                ?: run {
                    aapsLogger.debug(LTag.CORE, "Instara check: missing latest timestamp -> request from latest=$latestRequestable")
                    sendHistoryRequestBroadcast(idStart = latestRequestable)
                    return@withContext Result.success()
                }

            val ageMs = now - latestTs

            // (5) No gap + latestTimestamp is new. Skip, no request sent.
            if (ageMs <= STALE_THRESHOLD_MS) {
                aapsLogger.debug(LTag.CORE, "Instara check: no gap + fresh ageMs=$ageMs -> no request")
                return@withContext Result.success()
            }

            // (6) No gap + latestTimestamp is old. Request with latest sgvId.
            aapsLogger.debug(LTag.CORE, "Instara check: no gap but stale ageMs=$ageMs -> request from latest=$latestRequestable")
            sendHistoryRequestBroadcast(idStart = latestRequestable)

            return@withContext Result.success()
        } catch (t: Throwable) {
            aapsLogger.debug(LTag.CORE, "Instara check failed workId=$id err=$t")
            return@withContext Result.success()
        } finally {
            // Always reschedule while the plugin is enabled.
            // OFF/ON is handled by skipping inside doWork() when toggle is OFF.
            scheduleNext(applicationContext)
            aapsLogger.debug(LTag.CORE, "Instara check END workId=$id")
        }
    }

    private data class DeviceMeta(val sgvStart: Long, val sgvMark: Int)

    private fun readLatestDevicePrefixFromMeta(): Long? {
        val raw = preferences.get(StringNonKey.InstaraDeviceMetaJson)
        val root = try {
            JSONObject(raw)
        } catch (_: Throwable) {
            return null
        }
        val keys = root.keys()
        if (!keys.hasNext()) return null
        return keys.next().toLongOrNull()
    }

    private fun readDeviceMeta(devicePrefix: Long): DeviceMeta? {
        val raw = preferences.get(StringNonKey.InstaraDeviceMetaJson)
        val root = try {
            JSONObject(raw)
        } catch (_: Throwable) {
            return null
        }
        val obj = root.optJSONObject(devicePrefix.toString()) ?: return null
        val start = obj.optLong("sgvStart", -1L)
        val mark = obj.optInt("sgvMark", -1)
        if (start <= 0L || mark < 0) return null
        return DeviceMeta(start, mark)
    }

    private fun Long.toInstaraSgvIdString(): String =
        if (this == 0L) "0" else String.format("%013d", this)

    private fun sendHistoryRequestBroadcast(idStart: Long) {
        // NOTE: action name is still called Teljane.
        val action = "info.nightscout.androidaps.action.REQUEST_Teljane_DATA"
        val targetPackage = "com.teljane.instara"

        val dataArray = JSONArray().apply {
            put(JSONObject().apply {
                put("version", 1)
                put("sgvId", idStart.toInstaraSgvIdString())
            })
        }

        val intent = Intent(action).apply {
            setPackage(targetPackage)
            putExtra("collection", "entries")
            putExtra("data", dataArray.toString())
        }

        aapsLogger.debug(LTag.CORE, "Instara history request SEND -> action=$action pkg=$targetPackage collection=entries data=$dataArray")
        applicationContext.sendBroadcast(intent)
    }

    private fun scheduleNext(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<InstaraStaleCheckWorker>()
            .setInitialDelay(REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(ctx).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            req
        )

        val nextAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(REPEAT_INTERVAL_MINUTES)
        // NOTE: if injection failed, aapsLogger may not exist; but scheduleNext() is only called after injectedOk check
        aapsLogger.debug(LTag.CORE, "Instara check RESCHEDULE -> unique=$UNIQUE_NAME delayMin=$REPEAT_INTERVAL_MINUTES nextAt=$nextAt")
    }

    companion object {

        private const val UNIQUE_NAME = "InstaraStaleCheckWorker"
        private const val REPEAT_INTERVAL_MINUTES = 6L
        private const val STALE_THRESHOLD_MS = 5L * 60_000L
        private const val INSTARA_MARK_FACTOR = 100_000L
        private const val INSTARA_MARK_MAX = 99_999

        fun ensureScheduled(ctx: Context, enabled: Boolean) {
            if (!enabled) {
                cancel(ctx)
                return
            }

            val req = OneTimeWorkRequestBuilder<InstaraStaleCheckWorker>()
                .setInitialDelay(0, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}