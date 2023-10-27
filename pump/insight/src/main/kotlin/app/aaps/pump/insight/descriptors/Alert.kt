package app.aaps.pump.insight.descriptors

import java.util.Objects

class Alert {

    var alertId = 0
    var alertCategory: AlertCategory? = null
    var alertType: AlertType? = null
    var alertStatus: AlertStatus? = null
    var tBRAmount = 0
    var tBRDuration = 0
    var programmedBolusAmount = 0.0
    var deliveredBolusAmount = 0.0
    var cartridgeAmount = 0.0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val alert = other as Alert
        if (alertId != alert.alertId) return false
        if (tBRAmount != alert.tBRAmount) return false
        if (tBRDuration != alert.tBRDuration) return false
        if (java.lang.Double.compare(alert.programmedBolusAmount, programmedBolusAmount) != 0) return false
        if (java.lang.Double.compare(alert.deliveredBolusAmount, deliveredBolusAmount) != 0) return false
        if (java.lang.Double.compare(alert.cartridgeAmount, cartridgeAmount) != 0) return false
        if (alertCategory !== alert.alertCategory) return false
        return alertType === alert.alertType && alertStatus === alert.alertStatus
    }

    override fun hashCode(): Int {
        return Objects.hash(alertId, alertCategory, alertType, alertStatus, tBRAmount, tBRDuration, programmedBolusAmount, deliveredBolusAmount, cartridgeAmount)
    }
}