package info.nightscout.androidaps.plugins.pump.insight.utils

import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertCategory
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertCategory.*
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType.*
import info.nightscout.androidaps.interfaces.ResourceHelper
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertUtils @Inject constructor(private val rh: ResourceHelper) {

    fun getAlertCode(alertType: AlertType) = rh.gs(when (alertType) {
        REMINDER_01    -> R.string.alert_r1_code
        REMINDER_02    -> R.string.alert_r2_code
        REMINDER_03    -> R.string.alert_r3_code
        REMINDER_04    -> R.string.alert_r4_code
        REMINDER_07    -> R.string.alert_r7_code
        WARNING_31     -> R.string.alert_w31_code
        WARNING_32     -> R.string.alert_w32_code
        WARNING_33     -> R.string.alert_w33_code
        WARNING_34     -> R.string.alert_w34_code
        WARNING_36     -> R.string.alert_w36_code
        WARNING_38     -> R.string.alert_w38_code
        WARNING_39     -> R.string.alert_w39_code
        MAINTENANCE_20 -> R.string.alert_m20_code
        MAINTENANCE_21 -> R.string.alert_m21_code
        MAINTENANCE_22 -> R.string.alert_m22_code
        MAINTENANCE_23 -> R.string.alert_m23_code
        MAINTENANCE_24 -> R.string.alert_m24_code
        MAINTENANCE_25 -> R.string.alert_m25_code
        MAINTENANCE_26 -> R.string.alert_m26_code
        MAINTENANCE_27 -> R.string.alert_m27_code
        MAINTENANCE_28 -> R.string.alert_m28_code
        MAINTENANCE_29 -> R.string.alert_m29_code
        MAINTENANCE_30 -> R.string.alert_m30_code
        ERROR_6        -> R.string.alert_e6_code
        ERROR_10       -> R.string.alert_e10_code
        ERROR_13       -> R.string.alert_e13_code
    })

    fun getAlertTitle(alertType: AlertType) = rh.gs(when (alertType) {
        REMINDER_01    -> R.string.alert_r1_title
        REMINDER_02    -> R.string.alert_r2_title
        REMINDER_03    -> R.string.alert_r3_title
        REMINDER_04    -> R.string.alert_r4_title
        REMINDER_07    -> R.string.alert_r7_title
        WARNING_31     -> R.string.alert_w31_title
        WARNING_32     -> R.string.alert_w32_title
        WARNING_33     -> R.string.alert_w33_title
        WARNING_34     -> R.string.alert_w34_title
        WARNING_36     -> R.string.alert_w36_title
        WARNING_38     -> R.string.alert_w38_title
        WARNING_39     -> R.string.alert_w39_title
        MAINTENANCE_20 -> R.string.alert_m20_title
        MAINTENANCE_21 -> R.string.alert_m21_title
        MAINTENANCE_22 -> R.string.alert_m22_title
        MAINTENANCE_23 -> R.string.alert_m23_title
        MAINTENANCE_24 -> R.string.alert_m24_title
        MAINTENANCE_25 -> R.string.alert_m25_title
        MAINTENANCE_26 -> R.string.alert_m26_title
        MAINTENANCE_27 -> R.string.alert_m27_title
        MAINTENANCE_28 -> R.string.alert_m28_title
        MAINTENANCE_29 -> R.string.alert_m29_title
        MAINTENANCE_30 -> R.string.alert_m30_title
        ERROR_6        -> R.string.alert_e6_title
        ERROR_10       -> R.string.alert_e10_title
        ERROR_13       -> R.string.alert_e13_title
    })

    fun getAlertDescription(alert: Alert): String? {
        val decimalFormat = DecimalFormat("##0.00")
        val hours = alert.tBRDuration / 60
        val minutes = alert.tBRDuration - hours * 60
        return when (alert.alertType) {
            REMINDER_01    -> null
            REMINDER_02    -> null
            REMINDER_03    -> null
            REMINDER_04    -> null
            REMINDER_07    -> rh.gs(R.string.alert_r7_description, alert.tBRAmount, DecimalFormat("#0").format(hours.toLong()) + ":" + DecimalFormat("00").format(minutes.toLong()))
            WARNING_31     -> rh.gs(R.string.alert_w31_description, decimalFormat.format(alert.cartridgeAmount))
            WARNING_32     -> rh.gs(R.string.alert_w32_description)
            WARNING_33     -> rh.gs(R.string.alert_w33_description)
            WARNING_34     -> rh.gs(R.string.alert_w34_description)
            WARNING_36     -> rh.gs(R.string.alert_w36_description, alert.tBRAmount, DecimalFormat("#0").format(hours.toLong()) + ":" + DecimalFormat("00").format(minutes.toLong()))
            WARNING_38     -> rh.gs(R.string.alert_w38_description, decimalFormat.format(alert.programmedBolusAmount), decimalFormat.format(alert.deliveredBolusAmount))
            WARNING_39     -> null
            MAINTENANCE_20 -> rh.gs(R.string.alert_m20_description)
            MAINTENANCE_21 -> rh.gs(R.string.alert_m21_description)
            MAINTENANCE_22 -> rh.gs(R.string.alert_m22_description)
            MAINTENANCE_23 -> rh.gs(R.string.alert_m23_description)
            MAINTENANCE_24 -> rh.gs(R.string.alert_m24_description)
            MAINTENANCE_25 -> rh.gs(R.string.alert_m25_description)
            MAINTENANCE_26 -> rh.gs(R.string.alert_m26_description)
            MAINTENANCE_27 -> rh.gs(R.string.alert_m27_description)
            MAINTENANCE_28 -> rh.gs(R.string.alert_m28_description)
            MAINTENANCE_29 -> rh.gs(R.string.alert_m29_description)
            MAINTENANCE_30 -> rh.gs(R.string.alert_m30_description)
            ERROR_6        -> rh.gs(R.string.alert_e6_description)
            ERROR_10       -> rh.gs(R.string.alert_e10_description)
            ERROR_13       -> rh.gs(R.string.alert_e13_description)
            else           -> null
        }
    }

    fun getAlertIcon(alertCategory: AlertCategory) = when (alertCategory) {
        ERROR       -> R.drawable.ic_error
        MAINTENANCE -> R.drawable.ic_maintenance
        WARNING     -> R.drawable.ic_warning
        REMINDER    -> R.drawable.ic_reminder
    }
}