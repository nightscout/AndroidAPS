package info.nightscout.androidaps.receivers

import android.content.Context
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration
import info.nightscout.androidaps.plugins.general.maintenance.MaintenancePlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.extensions.buildDeviceStatus
import info.nightscout.androidaps.widget.updateWidget
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
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

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    companion object {

        private val STATUS_UPDATE_FREQUENCY = T.mins(15).msecs()
        private const val IOB_UPDATE_FREQUENCY_IN_MINUTES = 5L

        private var lastReadStatus: Long = 0
        private var lastRun: Long = 0
        private var lastIobUpload: Long = 0

    }

    override fun doWork(): Result {
        aapsLogger.debug(LTag.CORE, "KeepAlive received from: " + inputData.getString("schedule"))

        // 15 min interval is WorkManager minimum so schedule another instances to have 5 min interval
        if (inputData.getString("schedule") == "KeepAlive") {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "KeepAlive_5",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(KeepAliveWorker::class.java)
                    .setInputData(Data.Builder().putString("schedule", "KeepAlive_5").build())
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build()
            )
            WorkManager.getInstance(context).enqueueUniqueWork(
                "KeepAlive_10",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(KeepAliveWorker::class.java)
                    .setInputData(Data.Builder().putString("schedule", "KeepAlive_10").build())
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .build()
            )
        }

        updateWidget(context)
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.checkStaleBGAlert()
        checkPump()
        checkAPS()
        maintenancePlugin.deleteLogs(30)
        workerDbStatus()

        return Result.success()
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
        val isStatusOutdated = lastConnection + STATUS_UPDATE_FREQUENCY < System.currentTimeMillis()
        val isBasalOutdated = abs(requestedProfile.getBasal() - pump.baseBasalRate) > pump.pumpDescription.basalStep
        aapsLogger.debug(LTag.CORE, "Last connection: " + dateUtil.dateAndTimeString(lastConnection))
        // sometimes keep alive broadcast stops
        // as as workaround test if readStatus was requested before an alarm is generated
        if (lastReadStatus != 0L && lastReadStatus > System.currentTimeMillis() - T.mins(5).msecs()) {
            localAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, loop.isDisconnected)
        }
        if (loop.isDisconnected) {
            // do nothing if pump is disconnected
        } else if (runningProfile == null || ((!pump.isThisProfileSet(requestedProfile) || !requestedProfile.isEqual(runningProfile)) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE))) {
            rxBus.send(EventProfileSwitchChanged())
        } else if (isStatusOutdated && !pump.isBusy()) {
            lastReadStatus = System.currentTimeMillis()
            commandQueue.readStatus(rh.gs(R.string.keepalive_status_outdated), null)
        } else if (isBasalOutdated && !pump.isBusy()) {
            lastReadStatus = System.currentTimeMillis()
            commandQueue.readStatus(rh.gs(R.string.keepalive_basal_outdated), null)
        }
        if (lastRun != 0L && System.currentTimeMillis() - lastRun > T.mins(10).msecs()) {
            aapsLogger.error(LTag.CORE, "KeepAlive fail")
            fabricPrivacy.logCustom("KeepAliveFail")
        }
        lastRun = System.currentTimeMillis()
    }
}