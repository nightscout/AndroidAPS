package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.CartridgeStatus
import info.nightscout.androidaps.plugins.pump.insight.descriptors.CartridgeType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SymbolStatus
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

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