package info.nightscout.androidaps.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.T
import org.slf4j.LoggerFactory
import javax.inject.Inject
import kotlin.math.abs

class KeepAliveReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loopPlugin: LoopPlugin

    private var lastReadStatus: Long = 0
    private var lastRun: Long = 0
    private var lastIobUpload: Long = 0

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (L.isEnabled(L.CORE))
            log.debug("KeepAlive received")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:KeepAliveReceiver")
        wl.acquire(T.mins(2).msecs())
        LocalAlertUtils.shortenSnoozeInterval()
        LocalAlertUtils.checkStaleBGAlert()
        checkPump()
        checkAPS()
        wl.release()
    }

    companion object {
        private val log = LoggerFactory.getLogger(L.CORE)

        private val KEEP_ALIVE_MILLISECONDS = T.mins(5).msecs()
        private val STATUS_UPDATE_FREQUENCY = T.mins(15).msecs()
        private val IOB_UPDATE_FREQUENCY = T.mins(5).msecs()

        //called by MainApp at first app start
        @JvmStatic
        fun setAlarm(context: Context) {
            if (L.isEnabled(L.CORE))
                log.debug("KeepAlive scheduled")
            SystemClock.sleep(5000) // wait for app initialization
            LocalAlertUtils.shortenSnoozeInterval()
            LocalAlertUtils.presnoozeAlarms()
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

        @JvmStatic
        fun cancelAlarm(context: Context) {
            if (L.isEnabled(L.CORE))
                log.debug("KeepAlive canceled")
            val intent = Intent(context, KeepAliveReceiver::class.java)
            val sender = PendingIntent.getBroadcast(context, 0, intent, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(sender)
        }
    }

    // Usually devicestatus is uploaded through LoopPlugin after every loop cycle.
    // if there is no BG available, we have to upload anyway to have correct
    // IOB displayed in NS
    private fun checkAPS() {
        val usedAPS = configBuilderPlugin.activeAPS
        var shouldUploadStatus = false
        if (Config.NSCLIENT) return
        if (Config.PUMPCONTROL) shouldUploadStatus = true
        if (usedAPS == null || !loopPlugin.isEnabled() || DatabaseHelper.actualBg() == null)
            shouldUploadStatus = true
        else if (DateUtil.isOlderThan(usedAPS.lastAPSRun, 5)) shouldUploadStatus = true
        if (DateUtil.isOlderThan(lastIobUpload, IOB_UPDATE_FREQUENCY) && shouldUploadStatus) {
            lastIobUpload = DateUtil.now()
            NSUpload.uploadDeviceStatus(loopPlugin)
        }
    }

    private fun checkPump() {
        val pump = configBuilderPlugin.activePump ?: return
        val profile = profileFunction.getProfile() ?: return
        val lastConnection = pump.lastDataTime()
        val isStatusOutdated = lastConnection + STATUS_UPDATE_FREQUENCY < System.currentTimeMillis()
        val isBasalOutdated = abs(profile.basal - pump.baseBasalRate) > pump.pumpDescription.basalStep
        if (L.isEnabled(L.CORE))
            log.debug("Last connection: " + DateUtil.dateAndTimeString(lastConnection))
        // sometimes keep alive broadcast stops
        // as as workaround test if readStatus was requested before an alarm is generated
        if (lastReadStatus != 0L && lastReadStatus > System.currentTimeMillis() - T.mins(5).msecs()) {
            LocalAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, loopPlugin.isDisconnected)
        }
        if (!pump.isThisProfileSet(profile) && !configBuilderPlugin.commandQueue.isRunning(Command.CommandType.BASALPROFILE)) {
            rxBus.send(EventProfileNeedsUpdate())
        } else if (isStatusOutdated && !pump.isBusy) {
            lastReadStatus = System.currentTimeMillis()
            configBuilderPlugin.commandQueue.readStatus("KeepAlive. Status outdated.", null)
        } else if (isBasalOutdated && !pump.isBusy) {
            lastReadStatus = System.currentTimeMillis()
            configBuilderPlugin.commandQueue.readStatus("KeepAlive. Basal outdated.", null)
        }
        if (lastRun != 0L && System.currentTimeMillis() - lastRun > T.mins(10).msecs()) {
            log.error("KeepAlive fail")
            FabricPrivacy.getInstance().logCustom("KeepAliveFail")
        }
        lastRun = System.currentTimeMillis()
    }
}