package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.TotalDailyDose
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

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