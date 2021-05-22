package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertCategory
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetActiveAlertMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    var alert: Alert? = null
        private set

    override fun parse(byteBuf: ByteBuf?) {
        val alert = Alert()
        alert.alertId = byteBuf!!.readUInt16LE()
        alert.alertCategory = AlertCategory.fromId(byteBuf.readUInt16LE())
        alert.alertType = AlertType.fromId(byteBuf.readUInt16LE())
        alert.alertStatus = AlertStatus.fromId(byteBuf.readUInt16LE())
        if (alert.alertType != null) {
            when (alert.alertType) {
                AlertType.WARNING_38                        -> {
                    byteBuf.shift(4)
                    alert.programmedBolusAmount = byteBuf.readUInt16Decimal()
                    alert.deliveredBolusAmount = byteBuf.readUInt16Decimal()
                }

                AlertType.REMINDER_07, AlertType.WARNING_36 -> {
                    byteBuf.shift(2)
                    alert.tBRAmount = byteBuf.readUInt16LE()
                    alert.tBRDuration = byteBuf.readUInt16LE()
                }

                AlertType.WARNING_31                        -> alert.cartridgeAmount = byteBuf.readUInt16Decimal()
            }
        }
        if (alert.alertCategory != null && alert.alertType != null && alert.alertStatus != null) this.alert = alert
    }
}