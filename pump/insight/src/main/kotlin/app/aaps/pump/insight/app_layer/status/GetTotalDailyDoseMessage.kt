package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.TotalDailyDose
import app.aaps.pump.insight.utils.ByteBuf

class GetTotalDailyDoseMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var tDD: TotalDailyDose? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        tDD = TotalDailyDose().apply {
            byteBuf.let {
                bolus = it.readUInt32Decimal100()
                basal = it.readUInt32Decimal100()
                bolusAndBasal = it.readUInt32Decimal100()
            }
        }
    }
}