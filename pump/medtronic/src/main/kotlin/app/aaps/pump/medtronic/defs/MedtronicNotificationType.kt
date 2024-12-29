package app.aaps.pump.medtronic.defs

import app.aaps.core.interfaces.notifications.Notification
import app.aaps.pump.medtronic.R

/**
 * Created by andy on 10/15/18.
 */
enum class MedtronicNotificationType(
    var notificationType: Int,
    val resourceId: Int,
    val notificationUrgency: Int
) {

    PumpUnreachable(Notification.RILEYLINK_CONNECTION, R.string.medtronic_pump_status_pump_unreachable, Notification.NORMAL),  //
    PumpTypeNotSame(R.string.medtronic_error_pump_type_set_differs_from_detected, Notification.NORMAL),  //
    PumpBasalProfilesNotEnabled(R.string.medtronic_error_pump_basal_profiles_not_enabled, Notification.URGENT),  //
    PumpIncorrectBasalProfileSelected(R.string.medtronic_error_pump_incorrect_basal_profile_selected, Notification.URGENT),  //
    PumpWrongTBRTypeSet(R.string.medtronic_error_pump_wrong_tbr_type_set, Notification.URGENT),  //
    PumpWrongMaxBolusSet(R.string.medtronic_error_pump_wrong_max_bolus_set, Notification.NORMAL),  //
    PumpWrongMaxBasalSet(R.string.medtronic_error_pump_wrong_max_basal_set, Notification.NORMAL),  //
    PumpWrongTimeUrgent(R.string.medtronic_notification_check_time_date, Notification.URGENT),
    PumpWrongTimeNormal(R.string.medtronic_notification_check_time_date, Notification.NORMAL),
    TimeChangeOver24h(
        Notification.OVER_24H_TIME_CHANGE_REQUESTED, R.string.medtronic_error_pump_24h_time_change_requested, Notification.URGENT
    );

    constructor(resourceId: Int, notificationUrgency: Int) : this(Notification.MEDTRONIC_PUMP_ALARM, resourceId, notificationUrgency)
}