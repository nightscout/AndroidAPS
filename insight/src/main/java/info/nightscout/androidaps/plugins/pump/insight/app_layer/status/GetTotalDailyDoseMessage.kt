package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.TotalDailyDose
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetTotalDailyDoseMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    var tDD: TotalDailyDose? = null
        private set

    override fun parse(byteBuf: ByteBuf?) {
        tDD = TotalDailyDose()
        tDD?.let {
            byteBuf?.run {
                it.bolus = readUInt32Decimal100()
                it.basal = readUInt32Decimal100()
                it.bolusAndBasal = readUInt32Decimal100()
            }
        }
    }
}