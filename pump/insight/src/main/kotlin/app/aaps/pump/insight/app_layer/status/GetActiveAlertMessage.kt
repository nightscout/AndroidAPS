package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.AlertCategory
import app.aaps.pump.insight.descriptors.AlertStatus
import app.aaps.pump.insight.descriptors.AlertType
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

class GetActiveAlertMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var alert: app.aaps.pump.insight.descriptors.Alert? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        val alert = app.aaps.pump.insight.descriptors.Alert().apply {
            byteBuf.let {
                alertId = it.readUInt16LE()
                alertCategory = AlertCategory.fromId(it.readUInt16LE())
                alertType = AlertType.fromId(it.readUInt16LE())
                alertStatus = AlertStatus.fromId(it.readUInt16LE())
                if (alertType != null) {
                    when (alertType) {
                        AlertType.WARNING_38 -> {
                            it.shift(4)
                            programmedBolusAmount = it.readUInt16Decimal()
                            deliveredBolusAmount = it.readUInt16Decimal()
                        }

                        AlertType.REMINDER_07,
                        AlertType.WARNING_36 -> {
                            it.shift(2)
                            tBRAmount = it.readUInt16LE()
                            tBRDuration = it.readUInt16LE()
                        }

                        AlertType.WARNING_31 -> cartridgeAmount = it.readUInt16Decimal()
                        else                 -> Unit     // added to remove build warning
                    }
                }
            }
        }
        if (alert.alertCategory != null && alert.alertType != null && alert.alertStatus != null) this.alert = alert
    }
}