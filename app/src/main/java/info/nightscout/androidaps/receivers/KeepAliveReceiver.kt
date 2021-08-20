package info.nightscout.androidaps.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.T
import javax.inject.Inject
import kotlin.math.abs

class KeepAliveReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var config: Config
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var dateUtil: DateUtil

    companion object {
        private val KEEP_ALIVE_MILLISECONDS = T.mins(5).msecs()
        private val STATUS_UPDATE_FREQUENCY = T.mins(15).msecs()
        private val IOB_UPDATE_FREQUENCY_IN_MINS = 5L

        private var lastReadStatus: Long = 0
        private var lastRun: Long = 0
        private var lastIobUpload: Long = 0

    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        aapsLogger.debug(LTag.CORE, "KeepAlive received")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:KeepAliveReceiver")
        wl.acquire(T.mins(2).msecs())
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.checkStaleBGAlert()
        checkPump()
        checkAPS()
        wl.release()
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
            val pi = PendingIntent.getBroadcast(context, 0, i, 0)
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
            val sender = PendingIntent.getBroadcast(context, 0, intent, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(sender)
        }
    }

    // Usually deviceStatus is uploaded through LoopPlugin after every loop cycle.
    // if there is no BG available, we have to upload anyway to have correct
    // IOB displayed in NS
    private fun checkAPS() {
        var shouldUploadStatus = false
        if (config.NSCLIENT) return
        if (config.PUMPCONTROL) shouldUploadStatus = true
        else if (!loopPlugin.isEnabled() || iobCobCalculatorPlugin.actualBg() == null)
            shouldUploadStatus = true
        else if (DateUtil.isOlderThan(activePlugin.activeAPS.lastAPSRun, 5)) shouldUploadStatus = true
        if (DateUtil.isOlderThan(lastIobUpload, IOB_UPDATE_FREQUENCY_IN_MINS) && shouldUploadStatus) {
            lastIobUpload = DateUtil.now()
            nsUpload.uploadDeviceStatus(loopPlugin, iobCobCalculatorPlugin, profileFunction, activePlugin.activePump, receiverStatusStore, BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION)
        }
    }

    private fun checkPump() {
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile() ?: return
        val lastConnection = pump.lastDataTime()
        val isStatusOutdated = lastConnection + STATUS_UPDATE_FREQUENCY < System.currentTimeMillis()
        val isBasalOutdated = abs(profile.basal - pump.baseBasalRate) > pump.pumpDescription.basalStep
        aapsLogger.debug(LTag.CORE, "Last connection: " + dateUtil.dateAndTimeString(lastConnection))
        // sometimes keep alive broadcast stops
        // as as workaround test if readStatus was requested before an alarm is generated
        if (lastReadStatus != 0L && lastReadStatus > System.currentTimeMillis() - T.mins(5).msecs()) {
            localAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, loopPlugin.isDisconnected)
        }
        if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
            rxBus.send(EventProfileNeedsUpdate())
        } else if (isStatusOutdated && !pump.isBusy) {
            lastReadStatus = System.currentTimeMillis()
            commandQueue.readStatus("KeepAlive. Status outdated.", null)
        } else if (isBasalOutdated && !pump.isBusy) {
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