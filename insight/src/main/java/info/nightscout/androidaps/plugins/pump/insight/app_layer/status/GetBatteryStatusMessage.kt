package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BatteryStatus
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BatteryType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SymbolStatus
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetBatteryStatusMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    internal var batteryStatus: BatteryStatus? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        batteryStatus = BatteryStatus().apply {
            byteBuf.let {
                batteryType = BatteryType.fromId(it.readUInt16LE())
                batteryAmount = it.readUInt16LE()
                symbolStatus = SymbolStatus.fromId(it.readUInt16LE())
            }
        }
    }
}