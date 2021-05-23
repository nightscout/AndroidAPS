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

    override fun parse(byteBuf: ByteBuf?) {
        batteryStatus = BatteryStatus()
        batteryStatus?.let {
            byteBuf?.run {
                it.batteryType = BatteryType.fromId(readUInt16LE())
                it.batteryAmount = readUInt16LE()
                it.symbolStatus = SymbolStatus.fromId(readUInt16LE())
            }
        }
    }
}