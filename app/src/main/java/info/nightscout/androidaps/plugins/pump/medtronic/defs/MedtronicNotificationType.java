package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;

/**
 * Created by andy on 10/15/18.
 */

public enum MedtronicNotificationType {

    PumpUnreachable(Notification.RILEYLINK_CONNECTION, R.string.medtronic_pump_status_pump_unreachable, Notification.NORMAL), //
    PumpTypeNotSame(R.string.medtronic_error_pump_type_set_differs_from_detected, Notification.NORMAL), //
    PumpBasalProfilesNotEnabled(R.string.medtronic_error_pump_basal_profiles_not_enabled, Notification.URGENT), //
    PumpIncorrectBasalProfileSelected(R.string.medtronic_error_pump_incorrect_basal_profile_selected, Notification.URGENT), //
    PumpWrongTBRTypeSet(R.string.medtronic_error_pump_wrong_tbr_type_set, Notification.URGENT), //
    PumpWrongMaxBolusSet(R.string.medtronic_error_pump_wrong_max_bolus_set, Notification.NORMAL), //
    PumpWrongMaxBasalSet(R.string.medtronic_error_pump_wrong_max_basal_set, Notification.NORMAL), //
    PumpWrongTimeUrgent(R.string.combo_notification_check_time_date, Notification.URGENT),
    PumpWrongTimeNormal(R.string.combo_notification_check_time_date, Notification.NORMAL),
    TimeChangeOver24h(Notification.OVER_24H_TIME_CHANGE_REQUESTED, R.string.medtronic_error_pump_24h_time_change_requested, Notification.URGENT),
    //
    ;

    private int notificationType;
    private int resourceId;
    private int notificationUrgency;


    MedtronicNotificationType(int resourceId, int notificationUrgency) {
        this(Notification.MEDTRONIC_PUMP_ALARM, resourceId, notificationUrgency);
    }


    MedtronicNotificationType(int notificationType, int resourceId, int notificationUrgency) {
        this.notificationType = notificationType;
        this.resourceId = resourceId;
        this.notificationUrgency = notificationUrgency;
    }


    public int getNotificationType() {
        return notificationType;
    }


    public void setNotificationType(int notificationType) {
        this.notificationType = notificationType;
    }


    public int getResourceId() {

        return resourceId;
    }


    public int getNotificationUrgency() {

        return notificationUrgency;
    }

    // Notification.MEDTRONIC_PUMP_ALARM R.string.medtronic_pump_status_pump_unreachable, Notification.NORMAL

}
