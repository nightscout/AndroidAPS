package app.aaps.pump.medtronic.defs

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.pump.medtronic.R

enum class MedtronicNotificationType(
    val notificationId: NotificationId,
    val resourceId: Int,
    val notificationLevel: NotificationLevel
) {

    PumpUnreachable(NotificationId.RILEYLINK_CONNECTION, R.string.medtronic_pump_status_pump_unreachable, NotificationLevel.NORMAL),
    PumpTypeNotSame(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_error_pump_type_set_differs_from_detected, NotificationLevel.NORMAL),
    PumpBasalProfilesNotEnabled(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_error_pump_basal_profiles_not_enabled, NotificationLevel.URGENT),
    PumpIncorrectBasalProfileSelected(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_error_pump_incorrect_basal_profile_selected, NotificationLevel.URGENT),
    PumpWrongTBRTypeSet(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_error_pump_wrong_tbr_type_set, NotificationLevel.URGENT),
    PumpWrongMaxBolusSet(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_error_pump_wrong_max_bolus_set, NotificationLevel.NORMAL),
    PumpWrongMaxBasalSet(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_error_pump_wrong_max_basal_set, NotificationLevel.NORMAL),
    PumpWrongTimeUrgent(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_notification_check_time_date, NotificationLevel.URGENT),
    PumpWrongTimeNormal(NotificationId.MEDTRONIC_PUMP_ALARM, R.string.medtronic_notification_check_time_date, NotificationLevel.NORMAL),
    TimeChangeOver24h(NotificationId.OVER_24H_TIME_CHANGE_REQUESTED, R.string.medtronic_error_pump_24h_time_change_requested, NotificationLevel.URGENT);

    constructor(resourceId: Int, notificationLevel: NotificationLevel) : this(NotificationId.MEDTRONIC_PUMP_ALARM, resourceId, notificationLevel)
}
