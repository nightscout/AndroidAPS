package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

abstract class OccurrenceOfAlertEvent : HistoryEvent() {

    internal var alertType: AlertType? = null
        private set
    internal var alertID = 0
        private set

    override fun parse(byteBuf: ByteBuf?) {
        byteBuf?.run {
            alertType = AlertType.fromIncId(readUInt16LE())
            alertID = readUInt16LE()
        }
    }
}