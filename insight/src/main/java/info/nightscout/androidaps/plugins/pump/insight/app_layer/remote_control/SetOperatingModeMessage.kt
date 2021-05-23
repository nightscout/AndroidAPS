package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class SetOperatingModeMessage : AppLayerMessage(MessagePriority.HIGHEST, false, true, Service.REMOTE_CONTROL) {

    internal var operatingMode: OperatingMode? = null
    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2)
            byteBuf.putUInt16LE(operatingMode!!.id)
            return byteBuf
        }
}