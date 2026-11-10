package app.aaps.pump.danarkorean.comm

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.danar.comm.MessageBase
import kotlinx.coroutines.launch

class MsgInitConnStatusTimeK(
    injector: MetroMemberInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0301)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 < 10) {
            notificationManager.post(NotificationId.WRONG_DRIVER, TextRef.AndroidRes(app.aaps.pump.dana.R.string.pumpdrivercorrected))
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to export DanaR")
            // The same switch as in Configuration. Turning the plugins on and off here left the screens on the old driver.
            configBuilder.performPluginSwitch(danaRPlugin, true, PluginType.PUMP)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            // Queue-worker deadlock guard — don't unwrap the .launch. See CommandQueue kdoc.
            appScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change)) } // force new connection
            return
        }
        val time = dateTimeSecFromBuff(bytes, 0)
        val versionCode1 = intFromBuff(bytes, 6, 1)
        val versionCode2 = intFromBuff(bytes, 7, 1)
        val versionCode3 = intFromBuff(bytes, 8, 1)
        val versionCode4 = intFromBuff(bytes, 9, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil.dateAndTimeString(time))
        aapsLogger.debug(LTag.PUMPCOMM, "Version code1: $versionCode1")
        aapsLogger.debug(LTag.PUMPCOMM, "Version code2: $versionCode2")
        aapsLogger.debug(LTag.PUMPCOMM, "Version code3: $versionCode3")
        aapsLogger.debug(LTag.PUMPCOMM, "Version code4: $versionCode4")
    }
}