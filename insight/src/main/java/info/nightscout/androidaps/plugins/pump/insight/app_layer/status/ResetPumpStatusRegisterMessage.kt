package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ResetPumpStatusRegisterMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    var operatingModeChanged: Boolean = false
    var batteryStatusChanged = false
    var cartridgeStatusChanged = false
    var totalDailyDoseChanged = false
    var activeBasalRateChanged = false
    var activeTBRChanged = false
    var activeBolusesChanged = false
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