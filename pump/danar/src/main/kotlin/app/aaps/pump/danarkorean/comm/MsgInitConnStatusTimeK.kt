package app.aaps.pump.danarkorean.comm

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.pump.danar.comm.MessageBase
import dagger.android.HasAndroidInjector

class MsgInitConnStatusTimeK(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0301)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 < 10) {
            uiInteraction.addNotification(Notification.WRONG_DRIVER, rh.gs(app.aaps.pump.dana.R.string.pumpdrivercorrected), Notification.NORMAL)
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to export DanaR")
            danaRKoreanPlugin.setPluginEnabled(PluginType.PUMP, false)
            danaRKoreanPlugin.setFragmentVisible(PluginType.PUMP, false)
            danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
            danaRPlugin.setFragmentVisible(PluginType.PUMP, true)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            //If profile coming from pump, switch it as well
            configBuilder.storeSettings("ChangingKoreanDanaDriver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change), null) // force new connection
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