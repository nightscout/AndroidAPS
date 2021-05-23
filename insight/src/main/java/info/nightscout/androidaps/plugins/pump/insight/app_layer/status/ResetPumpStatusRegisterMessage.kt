package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ResetPumpStatusRegisterMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    internal var operatingModeChanged: Boolean = false
    internal var batteryStatusChanged = false
    internal var cartridgeStatusChanged = false
    internal var totalDailyDoseChanged = false
    internal var activeBasalRateChanged = false
    internal var activeTBRChanged = false
    internal var activeBolusesChanged = false

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(28)
            byteBuf.putBoolean(operatingModeChanged)
            byteBuf.putBoolean(batteryStatusChanged)
            byteBuf.putBoolean(cartridgeStatusChanged)
            byteBuf.putBoolean(totalDailyDoseChanged)
            byteBuf.putBoolean(activeBasalRateChanged)
            byteBuf.putBoolean(activeTBRChanged)
            byteBuf.putBoolean(activeBolusesChanged)
            for (i in 0..6) byteBuf.putBoolean(false)
            return byteBuf
        }
}