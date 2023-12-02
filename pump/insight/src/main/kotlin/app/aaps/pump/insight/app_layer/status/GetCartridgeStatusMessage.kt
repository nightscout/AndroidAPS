package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.CartridgeStatus
import app.aaps.pump.insight.descriptors.CartridgeType
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.SymbolStatus
import app.aaps.pump.insight.utils.ByteBuf

class GetCartridgeStatusMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    internal var cartridgeStatus: CartridgeStatus? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        cartridgeStatus = CartridgeStatus().apply {
            byteBuf.let {
                isInserted = it.readBoolean()
                cartridgeType = CartridgeType.fromId(it.readUInt16LE())
                symbolStatus = SymbolStatus.fromId(it.readUInt16LE())
                remainingAmount = it.readUInt16Decimal()
            }
        }
    }
}