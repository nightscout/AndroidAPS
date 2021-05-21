package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode.Companion.fromId
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class OperatingModeChangedEvent : HistoryEvent() {

    var oldValue: OperatingMode? = null
        private set
    var newValue: OperatingMode? = null
        private set

    override fun parse(byteBuf: ByteBuf?) {
        if (byteBuf != null) {
            oldValue = fromId(byteBuf.readUInt16LE())
            newValue = fromId(byteBuf.readUInt16LE())
        }
    }
}