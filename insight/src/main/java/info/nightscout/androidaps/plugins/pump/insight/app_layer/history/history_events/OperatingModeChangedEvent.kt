package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode.Companion.fromId
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class OperatingModeChangedEvent : HistoryEvent() {

    internal var oldValue: OperatingMode? = null
        private set
    internal var newValue: OperatingMode? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            oldValue = fromId(it.readUInt16LE())
            newValue = fromId(it.readUInt16LE())
        }
    }
}