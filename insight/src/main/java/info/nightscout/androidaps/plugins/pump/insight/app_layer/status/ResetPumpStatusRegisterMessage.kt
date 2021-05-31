package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ResetPumpStatusRegisterMessage(
    internal val operatingModeChanged: Boolean = false,
    internal val batteryStatusChanged: Boolean = false,
    internal val cartridgeStatusChanged: Boolean = false,
    internal val totalDailyDoseChanged: Boolean = false,
    internal val activeBasalRateChanged: Boolean = false,
    internal val activeTBRChanged: Boolean = false,
    internal val activeBolusesChanged: Boolean = false
) : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

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