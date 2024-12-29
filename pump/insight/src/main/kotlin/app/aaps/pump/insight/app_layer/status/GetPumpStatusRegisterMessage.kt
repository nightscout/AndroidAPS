package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

class GetPumpStatusRegisterMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    internal var isOperatingModeChanged = false
        private set
    internal var isBatteryStatusChanged = false
        private set
    internal var isCartridgeStatusChanged = false
        private set
    internal var isTotalDailyDoseChanged = false
        private set
    internal var isActiveBasalRateChanged = false
        private set
    internal var isActiveTBRChanged = false
        private set
    internal var isActiveBolusesChanged = false
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            isOperatingModeChanged = it.readBoolean()
            isBatteryStatusChanged = it.readBoolean()
            isCartridgeStatusChanged = it.readBoolean()
            isTotalDailyDoseChanged = it.readBoolean()
            isActiveBasalRateChanged = it.readBoolean()
            isActiveTBRChanged = it.readBoolean()
            isActiveBolusesChanged = it.readBoolean()
        }
    }
}