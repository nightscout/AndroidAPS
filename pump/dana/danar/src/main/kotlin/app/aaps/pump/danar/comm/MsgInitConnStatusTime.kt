package app.aaps.pump.danar.comm

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.launch

class MsgInitConnStatusTime(
    injector: MetroMemberInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0301)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 > 7) {
            notificationManager.post(NotificationId.WRONG_DRIVER, TextRef.AndroidRes(app.aaps.pump.dana.R.string.pumpdrivercorrected))
            danaRPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to Korean DanaR")
            // The same switch as in Configuration. Turning the plugins on and off here left the screens on the old driver.
            configBuilder.performPluginSwitch(danaRKoreanPlugin, true, PluginType.PUMP)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            // Queue-worker deadlock guard — don't unwrap the .launch. See CommandQueue kdoc.
            appScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change)) } // force new connection
            failed = false
            return
        } else {
            failed = true
        }
        val time = dateTimeSecFromBuff(bytes, 0)
        val versionCode = intFromBuff(bytes, 6, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil.dateAndTimeString(time))
        aapsLogger.debug(LTag.PUMPCOMM, "Version code: $versionCode")
    }
}