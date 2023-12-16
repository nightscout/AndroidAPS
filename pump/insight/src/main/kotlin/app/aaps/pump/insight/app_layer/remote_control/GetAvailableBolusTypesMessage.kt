package app.aaps.pump.insight.app_layer.remote_control

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.AvailableBolusTypes
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

class GetAvailableBolusTypesMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.REMOTE_CONTROL) {

    internal var availableBolusTypes: AvailableBolusTypes? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        availableBolusTypes = AvailableBolusTypes().apply {
            byteBuf.let {
                isStandardAvailable = it.readBoolean()
                isExtendedAvailable = it.readBoolean()
                isMultiwaveAvailable = it.readBoolean()
            }
        }
    }
}