package info.nightscout.androidaps.plugins.pump.insight.utils

import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertCategory
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertUtils @Inject constructor(private val resourceHelper: ResourceHelper) {

    fun getAlertCode(alertType: AlertType?) = when (alertType) {
        REMINDER_01    -> resourceHelper.gs(R.string.alert_r1_code)
        REMINDER_02    -> resourceHelper.gs(R.string.alert_r2_code)
        REMINDER_03    -> resourceHelper.gs(R.string.alert_r3_code)
        REMINDER_04    -> resourceHelper.gs(R.string.alert_r4_code)
        REMINDER_07    -> resourceHelper.gs(R.string.alert_r7_code)
        WARNING_31     -> resourceHelper.gs(R.string.alert_w31_code)
        WARNING_32     -> resourceHelper.gs(R.string.alert_w32_code)
        WARNING_33     -> resourceHelper.gs(R.string.alert_w33_code)
        WARNING_34     -> resourceHelper.gs(R.string.alert_w34_code)
        WARNING_36     -> resourceHelper.gs(R.string.alert_w36_code)
        WARNING_38     -> resourceHelper.gs(R.string.alert_w38_code)
        WARNING_39     -> resourceHelper.gs(R.string.alert_w39_code)
        MAINTENANCE_20 -> resourceHelper.gs(R.string.alert_m20_code)
        MAINTENANCE_21 -> resourceHelper.gs(R.string.alert_m21_code)
        MAINTENANCE_22 -> resourceHelper.gs(R.string.alert_m22_code)
        MAINTENANCE_23 -> resourceHelper.gs(R.string.alert_m23_code)
        MAINTENANCE_24 -> resourceHelper.gs(R.string.alert_m24_code)
        MAINTENANCE_25 -> resourceHelper.gs(R.string.alert_m25_code)
        MAINTENANCE_26 -> resourceHelper.gs(R.string.alert_m26_code)
        MAINTENANCE_27 -> resourceHelper.gs(R.string.alert_m27_code)
        MAINTENANCE_28 -> resourceHelper.gs(R.string.alert_m28_code)
        MAINTENANCE_29 -> resourceHelper.gs(R.string.alert_m29_code)
        MAINTENANCE_30 -> resourceHelper.gs(R.string.alert_m30_code)
        ERROR_6        -> resourceHelper.gs(R.string.alert_e6_code)
        ERROR_10       -> resourceHelper.gs(R.string.alert_e10_code)
        ERROR_13       -> resourceHelper.gs(R.string.alert_e13_code)
        else           -> ""
    }

    fun getAlertTitle(alertType: AlertType?) = when (alertType) {
        REMINDER_01    -> resourceHelper.gs(R.string.alert_r1_title)
        REMINDER_02    -> resourceHelper.gs(R.string.alert_r2_title)
        REMINDER_03    -> resourceHelper.gs(R.string.alert_r3_title)
        REMINDER_04    -> resourceHelper.gs(R.string.alert_r4_title)
        REMINDER_07    -> resourceHelper.gs(R.string.alert_r7_title)
        WARNING_31     -> resourceHelper.gs(R.string.alert_w31_title)
        WARNING_32     -> resourceHelper.gs(R.string.alert_w32_title)
        WARNING_33     -> resourceHelper.gs(R.string.alert_w33_title)
        WARNING_34     -> resourceHelper.gs(R.string.alert_w34_title)
        WARNING_36     -> resourceHelper.gs(R.string.alert_w36_title)
        WARNING_38     -> resourceHelper.gs(R.string.alert_w38_title)
        WARNING_39     -> resourceHelper.gs(R.string.alert_w39_title)
        MAINTENANCE_20 -> resourceHelper.gs(R.string.alert_m20_title)
        MAINTENANCE_21 -> resourceHelper.gs(R.string.alert_m21_title)
        MAINTENANCE_22 -> resourceHelper.gs(R.string.alert_m22_title)
        MAINTENANCE_23 -> resourceHelper.gs(R.string.alert_m23_title)
        MAINTENANCE_24 -> resourceHelper.gs(R.string.alert_m24_title)
        MAINTENANCE_25 -> resourceHelper.gs(R.string.alert_m25_title)
        MAINTENANCE_26 -> resourceHelper.gs(R.string.alert_m26_title)
        MAINTENANCE_27 -> resourceHelper.gs(R.string.alert_m27_title)
        MAINTENANCE_28 -> resourceHelper.gs(R.string.alert_m28_title)
        MAINTENANCE_29 -> resourceHelper.gs(R.string.alert_m29_title)
        MAINTENANCE_30 -> resourceHelper.gs(R.string.alert_m30_title)
        ERROR_6        -> resourceHelper.gs(R.string.alert_e6_title)
        ERROR_10       -> resourceHelper.gs(R.string.alert_e10_title)
        ERROR_13       -> resourceHelper.gs(R.string.alert_e13_title)
        else           -> ""
    }

    fun getAlertDescription(alert: Alert): String? {
        val decimalFormat = DecimalFormat("##0.00")
        val hours = alert.tBRDuration / 60
        val minutes = alert.tBRDuration - hours * 60
        return when (alert.alertType) {
            REMINDER_01    -> null
            REMINDER_02    -> null
            REMINDER_03    -> null
            REMINDER_04    -> null
            REMINDER_07    -> resourceHelper.gs(R.string.alert_r7_description, alert.tBRAmount, DecimalFormat("#0").format(hours.toLong()) + ":" + DecimalFormat("00").format(minutes.toLong()))
            WARNING_31     -> resourceHelper.gs(R.string.alert_w31_description, decimalFormat.format(alert.cartridgeAmount))
            WARNING_32     -> resourceHelper.gs(R.string.alert_w32_description)
            WARNING_33     -> resourceHelper.gs(R.string.alert_w33_description)
            WARNING_34     -> resourceHelper.gs(R.string.alert_w34_description)
            WARNING_36     -> resourceHelper.gs(R.string.alert_w36_description, alert.tBRAmount, DecimalFormat("#0").format(hours.toLong()) + ":" + DecimalFormat("00").format(minutes.toLong()))
            WARNING_38     -> resourceHelper.gs(R.string.alert_w38_description, decimalFormat.format(alert.programmedBolusAmount), decimalFormat.format(alert.deliveredBolusAmount))
            WARNING_39     -> null
            MAINTENANCE_20 -> resourceHelper.gs(R.string.alert_m20_description)
            MAINTENANCE_21 -> resourceHelper.gs(R.string.alert_m21_description)
            MAINTENANCE_22 -> resourceHelper.gs(R.string.alert_m22_description)
            MAINTENANCE_23 -> resourceHelper.gs(R.string.alert_m23_description)
            MAINTENANCE_24 -> resourceHelper.gs(R.string.alert_m24_description)
            MAINTENANCE_25 -> resourceHelper.gs(R.string.alert_m25_description)
            MAINTENANCE_26 -> resourceHelper.gs(R.string.alert_m26_description)
            MAINTENANCE_27 -> resourceHelper.gs(R.string.alert_m27_description)
            MAINTENANCE_28 -> resourceHelper.gs(R.string.alert_m28_description)
            MAINTENANCE_29 -> resourceHelper.gs(R.string.alert_m29_description)
            MAINTENANCE_30 -> resourceHelper.gs(R.string.alert_m30_description)
            ERROR_6        -> resourceHelper.gs(R.string.alert_e6_description)
            ERROR_10       -> resourceHelper.gs(R.string.alert_e10_description)
            ERROR_13       -> resourceHelper.gs(R.string.alert_e13_description)
            else           -> null
        }
    }

    fun getAlertIcon(alertCategory: AlertCategory?) = when (alertCategory) {
        AlertCategory.ERROR       -> R.drawable.ic_error
        AlertCategory.MAINTENANCE -> R.drawable.ic_maintenance
        AlertCategory.WARNING     -> R.drawable.ic_warning
        AlertCategory.REMINDER    -> R.drawable.ic_reminder
        else                      -> R.drawable.ic_error
    }
}