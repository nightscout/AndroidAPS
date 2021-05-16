package info.nightscout.androidaps.plugins.pump.insight.descriptors

class Alert {

    var alertId = 0
    var alertCategory: AlertCategory = AlertCategory.NONE
    var alertType: AlertType = AlertType.NONE
    var alertStatus: AlertStatus? = null
    var tBRAmount = 0
    var tBRDuration = 0
    var programmedBolusAmount = 0.0
    var deliveredBolusAmount = 0.0
    var cartridgeAmount = 0.0

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val alert = o as Alert
        if (alertId != alert.alertId) return false
        if (tBRAmount != alert.tBRAmount) return false
        if (tBRDuration != alert.tBRDuration) return false
        if (java.lang.Double.compare(alert.programmedBolusAmount, programmedBolusAmount) != 0) return false
        if (java.lang.Double.compare(alert.deliveredBolusAmount, deliveredBolusAmount) != 0) return false
        if (java.lang.Double.compare(alert.cartridgeAmount, cartridgeAmount) != 0) return false
        if (alertCategory !== alert.alertCategory) return false
        return if (alertType !== alert.alertType) false else alertStatus === alert.alertStatus
    }
}