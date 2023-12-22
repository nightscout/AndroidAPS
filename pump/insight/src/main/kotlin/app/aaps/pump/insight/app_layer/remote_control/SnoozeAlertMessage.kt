package app.aaps.pump.insight.app_layer.remote_control

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

class SnoozeAlertMessage : AppLayerMessage(MessagePriority.NORMAL, false, true, Service.REMOTE_CONTROL) {

    internal var alertID = 0
    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2).apply { putUInt16LE(alertID) }
            return byteBuf
        }
}