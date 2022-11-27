package info.nightscout.androidaps.receivers

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.configuration.maintenance.MaintenancePlugin
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.utils.receivers.ReceiverStatusStore
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.LocalAlertUtils
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.configBuilder.RunningConfiguration
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.plugins.sync.nsclient.extensions.buildDeviceStatus
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventProfileSwitchChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.ui.widget.Widget
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class KeepAliveWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var config: Config
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var runningConfiguration: RunningConfiguration
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

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

    override fun doWork(): Result {
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

        Widget.updateWidget(context)
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
        else if (!(loop as PluginBase).isEnabled() || iobCobCalculator.ads.actualBg() == null)
            shouldUploadStatus = true
        else if (dateUtil.isOlderThan(activePlugin.activeAPS.lastAPSRun, 5)) shouldUploadStatus = true
        if (dateUtil.isOlderThan(lastIobUpload, IOB_UPDATE_FREQUENCY_IN_MINUTES) && shouldUploadStatus) {
            lastIobUpload = dateUtil.now()
            buildDeviceStatus(
                dateUtil, loop, iobCobCalculator, profileFunction,
                activePlugin.activePump, receiverStatusStore, runningConfiguration,
                BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION
            )?.also {
                repository.insert(it)
            }
        }
    }

    private fun checkPump() {
        val pump = activePlugin.activePump
        val ps = profileFunction.getRequestedProfile() ?: return
        val requestedProfile = ProfileSealed.PS(ps)
        val runningProfile = profileFunction.getProfile()
        val lastConnection = pump.lastDataTime()
        val isStatusOutdated = lastConnection + STATUS_UPDATE_FREQUENCY < dateUtil.now()
        val isBasalOutdated = abs(requestedProfile.getBasal() - pump.baseBasalRate) > pump.pumpDescription.basalStep
        aapsLogger.debug(LTag.CORE, "Last connection: " + dateUtil.dateAndTimeString(lastConnection))
        // sometimes keep alive broadcast stops
        // as as workaround test if readStatus was requested before an alarm is generated
        if (lastReadStatus != 0L && lastReadStatus > dateUtil.now() - T.mins(5).msecs()) {
            localAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, loop.isDisconnected)
        }
        if (loop.isDisconnected) {
            // do nothing if pump is disconnected
        } else if (runningProfile == null || ((!pump.isThisProfileSet(requestedProfile) || !requestedProfile.isEqual(runningProfile)) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE))) {
            rxBus.send(EventProfileSwitchChanged())
        } else if (isStatusOutdated && !pump.isBusy()) {
            lastReadStatus = dateUtil.now()
            commandQueue.readStatus(rh.gs(R.string.keepalive_status_outdated), null)
        } else if (isBasalOutdated && !pump.isBusy()) {
            lastReadStatus = dateUtil.now()
            commandQueue.readStatus(rh.gs(R.string.keepalive_basal_outdated), null)
        }
    }
}