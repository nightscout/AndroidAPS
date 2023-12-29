package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.ActiveBolus
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

class GetActiveBolusesMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var activeBoluses: MutableList<ActiveBolus>? = null
    override fun parse(byteBuf: ByteBuf) {
        activeBoluses = mutableListOf()
        activeBoluses?.let {
            for (i in 0..2) {
                val activeBolus = ActiveBolus().apply {
                    byteBuf.let { it2 ->
                        bolusID = it2.readUInt16LE()
                        bolusType = BolusType.fromActiveId(it2.readUInt16LE())
                        it2.shift(2)
                        it2.shift(2)
                        initialAmount = it2.readUInt16Decimal()
                        remainingAmount = it2.readUInt16Decimal()
                        remainingDuration = it2.readUInt16LE()
                    }
                }
                if (activeBolus.bolusType != null) it.add(activeBolus)
            }
        }
    }
}