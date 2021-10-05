package info.nightscout.androidaps.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.DaggerBroadcastReceiver
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.extensions.buildDeviceStatus
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration
import info.nightscout.androidaps.plugins.general.maintenance.MaintenancePlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.T
import javax.inject.Inject
import kotlin.math.abs

class KeepAliveReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        private val KEEP_ALIVE_MILLISECONDS = T.mins(5).msecs()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        aapsLogger.debug(LTag.CORE, "KeepAlive received")

        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequest.Builder(KeepAliveWorker::class.java).build())
    }

    class KeepAliveWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var localAlertUtils: LocalAlertUtils
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var config: Config
        @Inject lateinit var iobCobCalculator: IobCobCalculator
        @Inject lateinit var loopPlugin: LoopPlugin
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var activePlugin: ActivePlugin
        @Inject lateinit var profileFunction: ProfileFunction
        @Inject lateinit var runningConfiguration: RunningConfiguration
        @Inject lateinit var receiverStatusStore: ReceiverStatusStore
        @Inject lateinit var rxBus: RxBusWrapper
        @Inject lateinit var commandQueue: CommandQueueProvider
        @Inject lateinit var fabricPrivacy: FabricPrivacy
        @Inject lateinit var maintenancePlugin: MaintenancePlugin

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
            localAlertUtils.shortenSnoozeInterval()
            localAlertUtils.checkStaleBGAlert()
            checkPump()
            checkAPS()
            maintenancePlugin.deleteLogs(30)

            return Result.success()
        }

        // Usually deviceStatus is uploaded through LoopPlugin after every loop cycle.
        // if there is no BG available, we have to upload anyway to have correct
        // IOB displayed in NS
        private fun checkAPS() {
            var shouldUploadStatus = false
            if (config.NSCLIENT) return
            if (config.PUMPCONTROL) shouldUploadStatus = true
            else if (!loopPlugin.isEnabled() || iobCobCalculator.ads.actualBg() == null)
                shouldUploadStatus = true
            else if (dateUtil.isOlderThan(activePlugin.activeAPS.lastAPSRun, 5)) shouldUploadStatus = true
            if (dateUtil.isOlderThan(lastIobUpload, IOB_UPDATE_FREQUENCY_IN_MINUTES) && shouldUploadStatus) {
                lastIobUpload = dateUtil.now()
                buildDeviceStatus(dateUtil, loopPlugin, iobCobCalculator, profileFunction,
                    activePlugin.activePump, receiverStatusStore, runningConfiguration,
                    BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION)?.also {
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
                localAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, loopPlugin.isDisconnected)
            }
            if (runningProfile == null || ((!pump.isThisProfileSet(requestedProfile) || !requestedProfile.isEqual(runningProfile)) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE))) {
                rxBus.send(EventProfileSwitchChanged())
            } else if (isStatusOutdated && !pump.isBusy()) {
                lastReadStatus = System.currentTimeMillis()
                commandQueue.readStatus("KeepAlive. Status outdated.", null)
            } else if (isBasalOutdated && !pump.isBusy()) {
                lastReadStatus = System.currentTimeMillis()
                commandQueue.readStatus("KeepAlive. Basal outdated.", null)
            }
            if (lastRun != 0L && System.currentTimeMillis() - lastRun > T.mins(10).msecs()) {
                aapsLogger.error(LTag.CORE, "KeepAlive fail")
                fabricPrivacy.logCustom("KeepAliveFail")
            }
            lastRun = System.currentTimeMillis()
        }
    }

    class KeepAliveManager @Inject constructor(
        private val aapsLogger: AAPSLogger,
        private val localAlertUtils: LocalAlertUtils
    ) {

        //called by MainApp at first app start
        fun setAlarm(context: Context) {
            aapsLogger.debug(LTag.CORE, "KeepAlive scheduled")
            SystemClock.sleep(5000) // wait for app initialization
            localAlertUtils.shortenSnoozeInterval()
            localAlertUtils.preSnoozeAlarms()
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(context, KeepAliveReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, 0, i, FLAG_IMMUTABLE)
            try {
                pi.send()
            } catch (e: CanceledException) {
            }
            am.cancel(pi)
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), KEEP_ALIVE_MILLISECONDS, pi)
        }

        fun cancelAlarm(context: Context) {
            aapsLogger.debug(LTag.CORE, "KeepAlive canceled")
            val intent = Intent(context, KeepAliveReceiver::class.java)
            val sender = PendingIntent.getBroadcast(context, 0, intent, FLAG_IMMUTABLE)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(sender)
        }
    }
}