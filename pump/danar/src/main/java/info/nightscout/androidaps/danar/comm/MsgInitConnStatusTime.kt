package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import dagger.android.HasAndroidInjector

class MsgInitConnStatusTime(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0301)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 > 7) {
            uiInteraction.addNotification(Notification.WRONG_DRIVER, rh.gs(info.nightscout.pump.dana.R.string.pumpdrivercorrected), Notification.NORMAL)
            danaRPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to Korean DanaR")
            danaRKoreanPlugin.setPluginEnabled(PluginType.PUMP, true)
            danaRKoreanPlugin.setFragmentVisible(PluginType.PUMP, true)
            danaRPlugin.setPluginEnabled(PluginType.PUMP, false)
            danaRPlugin.setFragmentVisible(PluginType.PUMP, false)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            //If profile coming from pump, switch it as well
            configBuilder.storeSettings("ChangingDanaDriver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change), null) // force new connection
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