package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveTBR
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetActiveTBRMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var activeTBR: ActiveTBR? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        val activeTBR = ActiveTBR().apply {
            byteBuf.let {
                percentage = it.readUInt16LE()
                remainingDuration = it.readUInt16LE()
                initialDuration = it.readUInt16LE()
            }
        }
        if (activeTBR.percentage != 100) this.activeTBR = activeTBR
    }
}