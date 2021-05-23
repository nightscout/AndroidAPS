package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBolus
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf
import java.util.*

class GetActiveBolusesMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var activeBoluses: MutableList<ActiveBolus>? = null
    override fun parse(byteBuf: ByteBuf?) {
        activeBoluses = mutableListOf()
        activeBoluses?.let {
            for (i in 0..2) {
                val activeBolus = ActiveBolus()
                byteBuf?.run {
                    activeBolus.bolusID = readUInt16LE()
                    activeBolus.bolusType = BolusType.fromActiveId(readUInt16LE())
                    shift(2)
                    shift(2)
                    activeBolus.initialAmount = readUInt16Decimal()
                    activeBolus.remainingAmount = readUInt16Decimal()
                    activeBolus.remainingDuration = readUInt16LE()
                }
                if (activeBolus.bolusType != null) it.add(activeBolus)
            }
        }
    }
}