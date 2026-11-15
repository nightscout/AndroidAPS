package app.aaps.pump.danarv2.comm

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MessageBase
import kotlinx.coroutines.launch

class MsgCheckValueV2(
    injector: MetroMemberInjector
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
            notificationManager.post(NotificationId.WRONG_DRIVER, TextRef.AndroidRes(app.aaps.pump.dana.R.string.pumpdrivercorrected))
            danaRPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to Korean DanaR")
            // The same switch as in Configuration. Turning the plugins on and off here left the screens on the old driver,
            // and it turned off the DanaR plugin, not this one.
            configBuilder.performPluginSwitch(danaRKoreanPlugin, true, PluginType.PUMP)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            // Queue-worker deadlock guard — don't unwrap the .launch. See CommandQueue kdoc.
            appScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change)) } // force new connection
            return
        }
        if (danaPump.protocol != 2) {
            notificationManager.post(NotificationId.WRONG_DRIVER, TextRef.AndroidRes(app.aaps.pump.dana.R.string.pumpdrivercorrected))
            danaRv2Plugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to non APS DanaR")
            configBuilder.performPluginSwitch(danaRPlugin, true, PluginType.PUMP)
            danaPump.reset() // mark not initialized
            pumpSync.connectNewPump()
            // Queue-worker deadlock guard — don't unwrap the .launch. See CommandQueue kdoc.
            appScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_driver_change)) } // force new connection
            return
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
    }
}
