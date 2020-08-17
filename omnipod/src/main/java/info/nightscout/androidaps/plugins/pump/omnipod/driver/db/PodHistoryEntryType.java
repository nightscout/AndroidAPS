package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup;
import info.nightscout.androidaps.plugins.pump.omnipod.R;

/**
 * Created by andy on 24.11.2019
 */
public enum PodHistoryEntryType {

    PairAndPrime(1, R.string.omnipod_init_pod_wizard_step2_title, PumpHistoryEntryGroup.Prime),
    FillCannulaSetBasalProfile(2, R.string.omnipod_init_pod_wizard_step4_title, PumpHistoryEntryGroup.Prime),
    DeactivatePod(3, R.string.omnipod_cmd_deactivate_pod, PumpHistoryEntryGroup.Prime),
    ResetPodState(4, R.string.omnipod_cmd_reset_pod, PumpHistoryEntryGroup.Prime),

    SetTemporaryBasal(10, R.string.omnipod_cmd_set_tbr, PumpHistoryEntryGroup.Basal),
    CancelTemporaryBasal(11, R.string.omnipod_cmd_cancel_tbr, PumpHistoryEntryGroup.Basal),
    CancelTemporaryBasalForce(12, R.string.omnipod_cmd_cancel_tbr_forced, PumpHistoryEntryGroup.Basal),

    SetBasalSchedule(20, R.string.omnipod_cmd_set_basal_schedule, PumpHistoryEntryGroup.Basal),

    GetPodStatus(30, R.string.omnipod_cmd_get_pod_status, PumpHistoryEntryGroup.Configuration),
    GetPodInfo(31, R.string.omnipod_cmd_get_pod_info, PumpHistoryEntryGroup.Configuration),
    SetTime(32, R.string.omnipod_cmd_set_time, PumpHistoryEntryGroup.Configuration),

    SetBolus(40, R.string.omnipod_cmd_set_bolus, PumpHistoryEntryGroup.Bolus),
    CancelBolus(41, R.string.omnipod_cmd_cancel_bolus, PumpHistoryEntryGroup.Bolus),

    ConfigureAlerts(50, R.string.omnipod_cmd_configure_alerts, PumpHistoryEntryGroup.Alarm),
    AcknowledgeAlerts(51, R.string.omnipod_cmd_acknowledge_alerts, PumpHistoryEntryGroup.Alarm),

    SuspendDelivery(60, R.string.omnipod_cmd_suspend_delivery, PumpHistoryEntryGroup.Basal),
    ResumeDelivery(61, R.string.omnipod_cmd_resume_delivery, PumpHistoryEntryGroup.Basal),

    UnknownEntryType(99, R.string.omnipod_cmd_unknown_entry);

    private int code;
    private static Map<Integer, PodHistoryEntryType> instanceMap;

    @StringRes
    private int resourceId;

    private PumpHistoryEntryGroup group;

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

    PodHistoryEntryType(int code, @StringRes int resourceId, PumpHistoryEntryGroup group) {
        this.code = code;
        this.resourceId = resourceId;
        this.group = group;
    }

    public int getCode() {
        return code;
    }

    public PumpHistoryEntryGroup getGroup() {
        return this.group;
    }

    public static PodHistoryEntryType getByCode(long code) {
        return getByCode((int) code);
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
