package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AvailableBolusTypes
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

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