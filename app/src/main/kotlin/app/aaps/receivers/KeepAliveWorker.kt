package app.aaps.receivers

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import com.google.common.util.concurrent.ListenableFuture
import info.nightscout.androidaps.R
import info.nightscout.database.impl.AppRepository
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class KeepAliveWorker(
    private val context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var config: Config
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP

    companion object {

        private val STATUS_UPDATE_FREQUENCY = T.mins(15).msecs()
        private const val IOB_UPDATE_FREQUENCY_IN_MINUTES = 5L

        private var lastReadStatus: Long = 0
        private var lastRun: Long = 0
        private var lastIobUpload: Long = 0

        const val KA_0 = "KeepAlive"
        private const val KA_5 = "KeepAlive_5"
        private const val KA_10 = "KeepAlive_10"
    }

    override suspend fun doWorkAndLog(): Result {
        aapsLogger.debug(LTag.CORE, "KeepAlive received from: " + inputData.getString("schedule"))

        // 15 min interval is WorkManager minimum so schedule another instances to have 5 min interval
        if (inputData.getString("schedule") == KA_0) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                KA_5,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(KeepAliveWorker::class.java)
                    .setInputData(Data.Builder().putString("schedule", KA_5).build())
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build()
            )
            WorkManager.getInstance(context).enqueueUniqueWork(
                KA_10,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(KeepAliveWorker::class.java)
                    .setInputData(Data.Builder().putString("schedule", KA_10).build())
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .build()
            )
        } else {
            // Sometimes schedule +5min, +10min gets broken
            // If this happen do nothing
            // It's causing false Pump unreachable alerts
            if (lastRun + T.mins(4).msecs() > dateUtil.now()) return Result.success(workDataOf("Error" to "Schedule broken. Ignoring"))
        }

        if (lastRun != 0L && dateUtil.now() - lastRun > T.mins(10).msecs()) {
            aapsLogger.error(LTag.CORE, "KeepAlive fail")
            fabricPrivacy.logCustom("KeepAliveFail")
        }
        lastRun = dateUtil.now()

        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.checkStaleBGAlert()
        checkPump()
        checkAPS()
        maintenancePlugin.deleteLogs(30)
        workerDbStatus()
        databaseCleanup()

        return Result.success()
    }

    // Perform history data cleanup every day
    // Keep 6 months
    private fun databaseCleanup() {
        val lastRun = sp.getLong(R.string.key_last_cleanup_run, 0L)
        if (lastRun < dateUtil.now() - T.days(1).msecs()) {
            val result = repository.cleanupDatabase(6 * 31, deleteTrackedChanges = false)
            aapsLogger.debug(LTag.CORE, "Cleanup result: $result")
            sp.putLong(R.string.key_last_cleanup_run, dateUtil.now())
        }
    }

    // When Worker DB grows too much, work operations become slow
    // Library is cleaning DB every 7 days which may not be sufficient for NSClient full sync
    private fun workerDbStatus() {
        val workQuery = WorkQuery.Builder
            .fromStates(listOf(WorkInfo.State.FAILED, WorkInfo.State.SUCCEEDED))
            .build()

        val workInfo: ListenableFuture<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfos(workQuery)
        aapsLogger.debug(LTag.CORE, "WorkManager size is ${workInfo.get().size}")
        if (workInfo.get().size > 1000) {
            WorkManager.getInstance(context).pruneWork()
            aapsLogger.debug(LTag.CORE, "WorkManager pruning ....")
        }
    }

    // Usually deviceStatus is uploaded through LoopPlugin after every loop cycle.
    // if there is no BG available, we have to upload anyway to have correct
    // IOB displayed in NS
    private fun checkAPS() {
        var shouldUploadStatus = false
        if (config.NSCLIENT) return
        if (config.PUMPCONTROL) shouldUploadStatus = true
        else if (!loop.isEnabled() || iobCobCalculator.ads.actualBg() == null) shouldUploadStatus = true
        else if (dateUtil.isOlderThan(activePlugin.activeAPS.lastAPSRun, 5)) shouldUploadStatus = true
        if (dateUtil.isOlderThan(lastIobUpload, IOB_UPDATE_FREQUENCY_IN_MINUTES) && shouldUploadStatus) {
            lastIobUpload = dateUtil.now()
            loop.buildAndStoreDeviceStatus()
        }
    }

    private fun checkPump() {
        val pump = activePlugin.activePump
        val ps = profileFunction.getRequestedProfile() ?: return
        val requestedProfile = ProfileSealed.PS(ps)
        val runningProfile = profileFunction.getProfile()
        val lastConnection = pump.lastDataTime()
        val now = dateUtil.now()
        val isStatusOutdated = lastConnection + STATUS_UPDATE_FREQUENCY < now
        val isBasalOutdated = abs(requestedProfile.getBasal() - pump.baseBasalRate) > pump.pumpDescription.basalStep
        aapsLogger.debug(LTag.CORE, "Last connection: " + dateUtil.dateAndTimeString(lastConnection))
        // Sometimes it can happen that keepalive is not triggered every 5 minutes as it should.
        // In some cases, it may not even have been started at all.
        // If these cases aren't handled, false "pump unreachable" alarms can be produced.
        // Avoid this by checking that (a) readStatus was requested at least once (lastReadStatus
        // is != 0 in that case) and (b) the last read status request was not too long ago.
        //
        // Also, use 5:30 as the threshold for (b) above instead of 5 minutes sharp. The keepalive
        // checks come in 5 minute intervals, but due to temporal jitter, the interval between the
        // last read status attempt and the current time can be slightly over 5 minutes (for example,
        // 300041 milliseconds instead of exactly 300000). Add 30 extra seconds to allow for
        // plenty of tolerance.
        if (lastReadStatus != 0L && (now - lastReadStatus).coerceIn(minimumValue = 0, maximumValue = null) <= T.secs(5 * 60 + 30).msecs()) {
            localAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, loop.isDisconnected)
        }
        if (loop.isDisconnected) {
            // do nothing if pump is disconnected
        } else if (runningProfile == null || ((!pump.isThisProfileSet(requestedProfile) || !requestedProfile.isEqual(runningProfile)
                || (runningProfile is ProfileSealed.EPS && runningProfile.value.originalEnd < dateUtil.now() && runningProfile.value.originalDuration != 0L))
                && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE))
        ) {
            rxBus.send(EventProfileSwitchChanged())
        } else if (isStatusOutdated && !pump.isBusy()) {
            lastReadStatus = now
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.keepalive_status_outdated), null)
        } else if (isBasalOutdated && !pump.isBusy()) {
            lastReadStatus = now
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.keepalive_basal_outdated), null)
        }
    }
}