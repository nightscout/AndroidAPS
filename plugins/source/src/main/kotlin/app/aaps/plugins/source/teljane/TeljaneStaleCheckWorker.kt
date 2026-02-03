package app.aaps.plugins.source.teljane

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min

class TeljaneStaleCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer

    init {
        // WorkManager instantiates workers; inject manually.
        // If injection fails, we still avoid crashing the app; logs will show the failure.
        try {
            (applicationContext as? HasAndroidInjector)
                ?.androidInjector()
                ?.inject(this)
        } catch (t: Throwable) {
            Log.e("TeljaneStaleCheckWorker", "Injection failed", t)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val enabled = inputData.getBoolean(KEY_ENABLED, true)
        val now = System.currentTimeMillis()

        aapsLogger.debug(
            LTag.CORE,
            "Teljane check START enabled=$enabled workId=$id attempt=$runAttemptCount now=$now"
        )

        try {
            if (!enabled) {
                aapsLogger.debug(LTag.CORE, "Teljane check SKIP (disabled) workId=$id")
                return@withContext Result.success()
            }

            // 1) Get current (latest) Teljane device prefix from DB.
            // Teljane encodes:
            //   sgvId = devicePrefix(8 digits) * 100000 + mark(0..99999)
            // so:
            //   devicePrefix = sgvId / 100000
            val devicePrefix: Long = persistenceLayer
                .getLatestTeljaneDeviceIdPrefix()
                .blockingGet()
                ?: run {
                    aapsLogger.debug(LTag.CORE, "Teljane check: no devicePrefix -> request sgvId=0")
                    sendHistoryRequestBroadcast(idStart = 0L)
                    return@withContext Result.success()
                }

            // 2) Fetch DB scope for this device.
            //
            // IMPORTANT Teljane contract:
            // - For each device, sgvMark should exist exactly once.
            // - That sgvMark is stored on the SAME ROW as the device's minSgvId row.
            //
            // We enforce this contract in the DAO query:
            // - It returns sgvMark from the min-sgvId row (ORDER BY sgvId ASC LIMIT 1).
            // Therefore:
            // - If sgvMark exists but is not on the min row -> DAO returns NULL -> scope.sgvMark becomes invalid.
            // - If sgvMark is missing entirely -> invalid as well.
            // In both abnormal cases, we request history from sgvId=0.
            val scope: PersistenceLayer.TeljaneScope = persistenceLayer
                .getTeljaneScope(devicePrefix)
                .blockingGet()
                ?: run {
                    aapsLogger.debug(LTag.CORE, "Teljane check: no scope rows for device=$devicePrefix -> request sgvId=0")
                    sendHistoryRequestBroadcast(idStart = 0L)
                    return@withContext Result.success()
                }

            val minSgvId = scope.minSgvId
            val maxSgvId = scope.maxSgvId
            val latestTimestamp = scope.latestTimestamp

            // If scope looks invalid, safest recovery is full history from 0.
            if (minSgvId <= 0L || maxSgvId <= 0L || latestTimestamp <= 0L) {
                aapsLogger.debug(
                    LTag.CORE,
                    "Teljane check: invalid scope for device=$devicePrefix -> request sgvId=0 " +
                        "minSgvId=$minSgvId maxSgvId=$maxSgvId latestTimestamp=$latestTimestamp scopeSgvMark=${scope.sgvMark}"
                )
                sendHistoryRequestBroadcast(idStart = 0L)
                return@withContext Result.success()
            }

            // Base for this device's 13-digit sgvId space.
            val deviceStart: Long = devicePrefix * TELJANE_MARK_FACTOR

            // Marks embedded in 13-digit ids (these are NOT the same meaning as sgvMark).
            val minMark = (minSgvId % TELJANE_MARK_FACTOR).toInt().coerceAtLeast(0)
            val maxMarkStored = (maxSgvId % TELJANE_MARK_FACTOR).toInt().coerceAtLeast(0)

            // sgvMark must exist (0..99999) for this device (from min-sgvId row via DAO).
            // If missing/invalid -> abnormal -> request from 0.
            val sgvMark: Int = scope.sgvMark
            if (sgvMark !in 0..TELJANE_MARK_MAX) {
                aapsLogger.debug(
                    LTag.CORE,
                    "Teljane check: sgvMark missing/invalid for device=$devicePrefix -> request sgvId=0 " +
                        "scopeSgvMark=$sgvMark minSgvId=$minSgvId maxSgvId=$maxSgvId latestTimestamp=$latestTimestamp"
                )
                sendHistoryRequestBroadcast(idStart = 0L)
                return@withContext Result.success()
            }

            // 3) Expected max sgvId for this device based on sgvMark:
            //   maxSgvIdExpected = deviceStart + sgvMark
            val maxSgvIdExpected: Long = deviceStart + sgvMark.toLong()

            // 4) Gap detection range:
            // Only scan up to already-received data (maxSgvId),
            // and never beyond expected max (maxSgvIdExpected).
            val endSgvId: Long = min(maxSgvId, maxSgvIdExpected)
            val endMark = (endSgvId % TELJANE_MARK_FACTOR).toInt().coerceAtLeast(0)

            if (endMark < minMark) {
                aapsLogger.debug(
                    LTag.CORE,
                    "Teljane check: invalid gap-scan scope device=$devicePrefix minMark=$minMark endMark=$endMark " +
                        "minSgvId=$minSgvId maxSgvId=$maxSgvId sgvMark=$sgvMark maxSgvIdExpected=$maxSgvIdExpected endSgvId=$endSgvId"
                )
                return@withContext Result.success()
            }

            aapsLogger.debug(
                LTag.CORE,
                "Teljane check: device=$devicePrefix " +
                    "minSgvId=$minSgvId maxSgvId=$maxSgvId latestTimestamp=$latestTimestamp " +
                    "sgvMark=$sgvMark maxSgvIdExpected=$maxSgvIdExpected endSgvId=$endSgvId " +
                    "minMark=$minMark maxMarkStored=$maxMarkStored endMark=$endMark"
            )

            // 5) Ask DB for the first missing mark in [minMark..endMark] for this device.
            val missingMark: Int? = persistenceLayer
                .findTeljaneFirstMissingMark(devicePrefix, minMark, endMark)
                .blockingGet()

            if (missingMark != null && missingMark >= 0) {
                val missingSgvId = deviceStart + missingMark.toLong()
                aapsLogger.debug(
                    LTag.CORE,
                    "Teljane check: GAP found device=$devicePrefix missingMark=$missingMark -> request sgvId=$missingSgvId"
                )
                sendHistoryRequestBroadcast(idStart = missingSgvId)
                return@withContext Result.success()
            }

            aapsLogger.debug(
                LTag.CORE,
                "Teljane check: device=$devicePrefix data complete (no missing) in [minMark=$minMark, endMark=$endMark]"
            )

            // 6) Staleness check: if latest reading is older than 5 minutes, request from maxSgvId.
            val ageMs = now - latestTimestamp
            val shouldRequest = ageMs > STALE_THRESHOLD_MS

            if (shouldRequest) {
                aapsLogger.debug(
                    LTag.CORE,
                    "Teljane check: device=$devicePrefix latest too old ageMs=$ageMs (> $STALE_THRESHOLD_MS) -> request from maxSgvId=$maxSgvId"
                )
                sendHistoryRequestBroadcast(idStart = maxSgvId)
            } else {
                aapsLogger.debug(
                    LTag.CORE,
                    "Teljane check: device=$devicePrefix latest fresh ageMs=$ageMs (<= $STALE_THRESHOLD_MS) -> no request"
                )
            }

            return@withContext Result.success()
        } catch (t: Throwable) {
            // Never crash the app from this periodic worker; log and exit.
            aapsLogger.debug(LTag.CORE, "Teljane check failed workId=$id err=$t")
            return@withContext Result.success()
        } finally {
            scheduleNext(applicationContext, enabled = enabled)
            aapsLogger.debug(LTag.CORE, "Teljane check END workId=$id")
        }
    }

    private fun Long.toTeljaneSgvIdString(): String =
        if (this == 0L) "0" else String.format("%013d", this)

    private fun sendHistoryRequestBroadcast(idStart: Long) {
        val action = "info.nightscout.androidaps.action.REQUEST_Teljane_DATA"
        val targetPackage = "com.teljane.instara.community"

        val dataArray = JSONArray().apply {
            put(JSONObject().apply {
                put("version", 1)
                put("sgvId", idStart.toTeljaneSgvIdString())
            })
        }

        val intent = Intent(action).apply {
            setPackage(targetPackage)
            putExtra("collection", "entries")
            putExtra("data", dataArray.toString())
        }

        aapsLogger.debug(
            LTag.CORE,
            "Teljane history request SEND -> action=$action pkg=$targetPackage collection=entries data=$dataArray"
        )
        applicationContext.sendBroadcast(intent)
    }

    private fun scheduleNext(ctx: Context, enabled: Boolean) {
        val req = OneTimeWorkRequestBuilder<TeljaneStaleCheckWorker>()
            .setInputData(workDataOf(KEY_ENABLED to enabled))
            .setInitialDelay(REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(ctx).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            req
        )

        val nextAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(REPEAT_INTERVAL_MINUTES)
        aapsLogger.debug(
            LTag.CORE,
            "Teljane check RESCHEDULE -> unique=$UNIQUE_NAME delayMin=$REPEAT_INTERVAL_MINUTES enabled=$enabled nextAt=$nextAt"
        )
    }

    companion object {
        private const val UNIQUE_NAME = "TeljaneStaleCheckWorker"
        private const val KEY_ENABLED = "enabled"

        private const val REPEAT_INTERVAL_MINUTES = 6L
        private const val STALE_THRESHOLD_MS = 5L * 60_000L

        // Teljane encoding:
        // sgvId = devicePrefix * 100000 + mark
        private const val TELJANE_MARK_FACTOR = 100_000L
        private const val TELJANE_MARK_MAX = 99_999

        fun ensureScheduled(ctx: Context, enabled: Boolean) {
            val req = OneTimeWorkRequestBuilder<TeljaneStaleCheckWorker>()
                .setInputData(workDataOf(KEY_ENABLED to enabled))
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