package info.nightscout.androidaps.danaRv2.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.pump.dana.DanaPump

class MsgCheckValue_v2(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0xF0F1)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.isNewPump = true
        aapsLogger.debug(LTag.PUMPCOMM, "New firmware confirmed")
        danaPump.hwModel = intFromBuff(bytes, 0, 1)
        danaPump.protocol = intFromBuff(bytes, 1, 1)
        danaPump.productCode = intFromBuff(bytes, 2, 1)
        if (danaPump.hwModel != DanaPump.EXPORT_MODEL) {
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
            configBuilder.storeSettings("ChangingDanaRv2Driver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change), null) // force new connection
            return
        }
        if (danaPump.protocol != 2) {
            uiInteraction.addNotification(Notification.WRONG_DRIVER, rh.gs(info.nightscout.pump.dana.R.string.pumpdrivercorrected), Notification.NORMAL)
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to non APS DanaR")
            danaRv2Plugin.setPluginEnabled(PluginType.PUMP, false)
            danaRv2Plugin.setFragmentVisible(PluginType.PUMP, false)
            danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
            danaRPlugin.setFragmentVisible(PluginType.PUMP, true)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            //If profile coming from pump, switch it as well
            configBuilder.storeSettings("ChangingDanaRv2Driver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change), null) // force new connection
            return
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
    }
}
