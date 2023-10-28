package app.aaps.pump.insight.app_layer.remote_control

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.utils.ByteBuf

class SetOperatingModeMessage : AppLayerMessage(MessagePriority.HIGHEST, false, true, Service.REMOTE_CONTROL) {

    internal lateinit var operatingMode: OperatingMode

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2).apply { putUInt16LE(operatingMode.id) }
            return byteBuf
        }
}