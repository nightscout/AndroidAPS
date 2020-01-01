package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 24.11.2019
 */
public enum PodHistoryEntryType {

    PairAndPrime(1, R.string.omnipod_init_pod_wizard_step2_title),
    FillCannulaSetBasalProfile(2, R.string.omnipod_init_pod_wizard_step4_title),
    DeactivatePod(3, R.string.omnipod_cmd_deactivate_pod),
    ResetPodState(4, R.string.omnipod_cmd_reset_pod),

    SetTemporaryBasal(10, R.string.omnipod_cmd_set_tbr),
    CancelTemporaryBasal(11, R.string.omnipod_cmd_cancel_tbr),
    CancelTemporaryBasalForce(12, R.string.omnipod_cmd_cancel_tbr_forced),

    SetBasalSchedule(20, R.string.omnipod_cmd_set_basal_schedule),

    GetPodStatus(30, R.string.omnipod_cmd_get_pod_status),
    GetPodInfo(31, R.string.omnipod_cmd_get_pod_info),
    SetTime(32, R.string.omnipod_cmd_set_time),

    SetBolus(40, R.string.omnipod_cmd_set_bolus),
    CancelBolus(41, R.string.omnipod_cmd_cancel_bolus),

    ConfigureAlerts(50, R.string.omnipod_cmd_configure_alerts),
    AcknowledgeAlerts(51, R.string.omnipod_cmd_acknowledge_alerts),

    SuspendDelivery(60, R.string.omnipod_cmd_suspend_delivery),
    ResumeDelivery(61, R.string.omnipod_cmd_resume_delivery),

    UnknownEntryType(99, R.string.omnipod_cmd_unknown_entry)
    ;

    private int code;
    private static Map<Integer, PodHistoryEntryType> instanceMap;

    @StringRes
    private int resourceId;


    static {
        instanceMap = new HashMap<>();

        for (PodHistoryEntryType value : values()) {
            instanceMap.put(value.code, value);
        }
    }


    PodHistoryEntryType(int code, @StringRes int resourceId) {
        this.code = code;
        this.resourceId = resourceId;
    }

    public int getCode() {
        return code;
    }


    public static PodHistoryEntryType getByCode(int code) {
        if (instanceMap.containsKey(code)) {
            return instanceMap.get(code);
        } else {
            return UnknownEntryType;
        }
    }

    public int getResourceId() {
        return resourceId;
    }
}
