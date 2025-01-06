package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.descriptors.AlertType
import app.aaps.pump.insight.utils.ByteBuf

abstract class OccurrenceOfAlertEvent : HistoryEvent() {

    internal var alertType: AlertType? = null
        private set
    internal var alertID = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            alertType = AlertType.fromIncId(it.readUInt16LE())
            alertID = it.readUInt16LE()
        }
    }
}