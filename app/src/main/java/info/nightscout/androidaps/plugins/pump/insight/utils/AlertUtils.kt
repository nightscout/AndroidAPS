package info.nightscout.androidaps.plugins.pump.insight.utils

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertCategory
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import java.text.DecimalFormat

fun getAlertCode(alertType: AlertType) = MainApp.gs(when (alertType) {
    AlertType.REMINDER_01 -> R.string.alert_r1_code
    AlertType.REMINDER_02 -> R.string.alert_r2_code
    AlertType.REMINDER_03 -> R.string.alert_r3_code
    AlertType.REMINDER_04 -> R.string.alert_r4_code
    AlertType.REMINDER_07 -> R.string.alert_r7_code
    AlertType.WARNING_31 -> R.string.alert_w31_code
    AlertType.WARNING_32 -> R.string.alert_w32_code
    AlertType.WARNING_33 -> R.string.alert_w33_code
    AlertType.WARNING_34 -> R.string.alert_w34_code
    AlertType.WARNING_36 -> R.string.alert_w36_code
    AlertType.WARNING_38 -> R.string.alert_w38_code
    AlertType.WARNING_39 -> R.string.alert_w39_code
    AlertType.MAINTENANCE_20 -> R.string.alert_m20_code
    AlertType.MAINTENANCE_21 -> R.string.alert_m21_code
    AlertType.MAINTENANCE_22 -> R.string.alert_m22_code
    AlertType.MAINTENANCE_23 -> R.string.alert_m23_code
    AlertType.MAINTENANCE_24 -> R.string.alert_m24_code
    AlertType.MAINTENANCE_25 -> R.string.alert_m25_code
    AlertType.MAINTENANCE_26 -> R.string.alert_m26_code
    AlertType.MAINTENANCE_27 -> R.string.alert_m27_code
    AlertType.MAINTENANCE_28 -> R.string.alert_m28_code
    AlertType.MAINTENANCE_29 -> R.string.alert_m29_code
    AlertType.MAINTENANCE_30 -> R.string.alert_m30_code
    AlertType.ERROR_6 -> R.string.alert_e6_code
    AlertType.ERROR_10 -> R.string.alert_e10_code
    AlertType.ERROR_13 -> R.string.alert_e13_code
})

fun getAlertTitle(alertType: AlertType) = MainApp.gs(when (alertType) {
    AlertType.REMINDER_01 -> R.string.alert_r1_title
    AlertType.REMINDER_02 -> R.string.alert_r2_title
    AlertType.REMINDER_03 -> R.string.alert_r3_title
    AlertType.REMINDER_04 -> R.string.alert_r4_title
    AlertType.REMINDER_07 -> R.string.alert_r7_title
    AlertType.WARNING_31 -> R.string.alert_w31_title
    AlertType.WARNING_32 -> R.string.alert_w32_title
    AlertType.WARNING_33 -> R.string.alert_w33_title
    AlertType.WARNING_34 -> R.string.alert_w34_title
    AlertType.WARNING_36 -> R.string.alert_w36_title
    AlertType.WARNING_38 -> R.string.alert_w38_title
    AlertType.WARNING_39 -> R.string.alert_w39_title
    AlertType.MAINTENANCE_20 -> R.string.alert_m20_title
    AlertType.MAINTENANCE_21 -> R.string.alert_m21_title
    AlertType.MAINTENANCE_22 -> R.string.alert_m22_title
    AlertType.MAINTENANCE_23 -> R.string.alert_m23_title
    AlertType.MAINTENANCE_24 -> R.string.alert_m24_title
    AlertType.MAINTENANCE_25 -> R.string.alert_m25_title
    AlertType.MAINTENANCE_26 -> R.string.alert_m26_title
    AlertType.MAINTENANCE_27 -> R.string.alert_m27_title
    AlertType.MAINTENANCE_28 -> R.string.alert_m28_title
    AlertType.MAINTENANCE_29 -> R.string.alert_m29_title
    AlertType.MAINTENANCE_30 -> R.string.alert_m30_title
    AlertType.ERROR_6 -> R.string.alert_e6_title
    AlertType.ERROR_10 -> R.string.alert_e10_title
    AlertType.ERROR_13 -> R.string.alert_e13_title
})

fun getAlertDescription(alert: Alert): String? {
    val decimalFormat = DecimalFormat("##0.00")
    val hours = alert.tbrDuration / 60
    val minutes = alert.tbrDuration - hours * 60
    return when (alert.alertType!!) {
        AlertType.REMINDER_01 -> null
        AlertType.REMINDER_02 -> null
        AlertType.REMINDER_03 -> null
        AlertType.REMINDER_04 -> null
        AlertType.REMINDER_07 -> MainApp.gs(R.string.alert_r7_description, alert.tbrAmount, DecimalFormat("#0").format(hours.toLong()) + ":" + DecimalFormat("00").format(minutes.toLong()))
        AlertType.WARNING_31 -> MainApp.gs(R.string.alert_w31_description, decimalFormat.format(alert.cartridgeAmount))
        AlertType.WARNING_32 -> MainApp.gs(R.string.alert_w32_description)
        AlertType.WARNING_33 -> MainApp.gs(R.string.alert_w33_description)
        AlertType.WARNING_34 -> MainApp.gs(R.string.alert_w34_description)
        AlertType.WARNING_36 -> MainApp.gs(R.string.alert_w36_description, alert.tbrAmount, DecimalFormat("#0").format(hours.toLong()) + ":" + DecimalFormat("00").format(minutes.toLong()))
        AlertType.WARNING_38 -> MainApp.gs(R.string.alert_w38_description, decimalFormat.format(alert.programmedBolusAmount), decimalFormat.format(alert.deliveredBolusAmount))
        AlertType.WARNING_39 -> null
        AlertType.MAINTENANCE_20 -> MainApp.gs(R.string.alert_m20_description)
        AlertType.MAINTENANCE_21 -> MainApp.gs(R.string.alert_m21_description)
        AlertType.MAINTENANCE_22 -> MainApp.gs(R.string.alert_m22_description)
        AlertType.MAINTENANCE_23 -> MainApp.gs(R.string.alert_m23_description)
        AlertType.MAINTENANCE_24 -> MainApp.gs(R.string.alert_m24_description)
        AlertType.MAINTENANCE_25 -> MainApp.gs(R.string.alert_m25_description)
        AlertType.MAINTENANCE_26 -> MainApp.gs(R.string.alert_m26_description)
        AlertType.MAINTENANCE_27 -> MainApp.gs(R.string.alert_m27_description)
        AlertType.MAINTENANCE_28 -> MainApp.gs(R.string.alert_m28_description)
        AlertType.MAINTENANCE_29 -> MainApp.gs(R.string.alert_m29_description)
        AlertType.MAINTENANCE_30 -> MainApp.gs(R.string.alert_m30_description)
        AlertType.ERROR_6 -> MainApp.gs(R.string.alert_e6_description)
        AlertType.ERROR_10 -> MainApp.gs(R.string.alert_e10_description)
        AlertType.ERROR_13 -> MainApp.gs(R.string.alert_e13_description)
    }
}

fun getAlertIcon(alertCategory: AlertCategory) = when (alertCategory) {
    AlertCategory.ERROR -> R.drawable.ic_error
    AlertCategory.MAINTENANCE -> R.drawable.ic_maintenance
    AlertCategory.WARNING -> R.drawable.ic_warning
    AlertCategory.REMINDER -> R.drawable.ic_reminder
}