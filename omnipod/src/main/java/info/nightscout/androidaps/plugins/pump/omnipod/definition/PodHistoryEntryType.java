package info.nightscout.androidaps.plugins.pump.omnipod.definition;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup;
import info.nightscout.androidaps.plugins.pump.omnipod.R;

/**
 * Created by andy on 24.11.2019
 */
public enum PodHistoryEntryType {

    INITIALIZE_POD(1, R.string.omnipod_cmd_initialize_pod, PumpHistoryEntryGroup.Prime),
    INSERT_CANNULA(2, R.string.omnipod_cmd_insert_cannula, PumpHistoryEntryGroup.Prime),
    DEACTIVATE_POD(3, R.string.omnipod_cmd_deactivate_pod, PumpHistoryEntryGroup.Prime),
    DISCARD_POD(4, R.string.omnipod_cmd_discard_pod, PumpHistoryEntryGroup.Prime),

    SET_TEMPORARY_BASAL(10, R.string.omnipod_cmd_set_tbr, PumpHistoryEntryGroup.Basal),
    CANCEL_TEMPORARY_BASAL_BY_DRIVER(11, R.string.omnipod_cmd_cancel_tbr_by_driver, PumpHistoryEntryGroup.Basal),
    CANCEL_TEMPORARY_BASAL(12, R.string.omnipod_cmd_cancel_tbr, PumpHistoryEntryGroup.Basal),
    SET_FAKE_SUSPENDED_TEMPORARY_BASAL(13, R.string.omnipod_cmd_set_fake_suspended_tbr, PumpHistoryEntryGroup.Basal),
    CANCEL_FAKE_SUSPENDED_TEMPORARY_BASAL(14, R.string.omnipod_cmd_cancel_fake_suspended_tbr, PumpHistoryEntryGroup.Basal),
    SPLIT_TEMPORARY_BASAL(15, R.string.omnipod_cmd_split_tbr, PumpHistoryEntryGroup.Basal),

    SET_BASAL_SCHEDULE(20, R.string.omnipod_cmd_set_basal_schedule, PumpHistoryEntryGroup.Basal),

    GET_POD_STATUS(30, R.string.omnipod_cmd_get_pod_status, PumpHistoryEntryGroup.Configuration),
    GET_POD_INFO(31, R.string.omnipod_cmd_get_pod_info, PumpHistoryEntryGroup.Configuration),
    SET_TIME(32, R.string.omnipod_cmd_set_time, PumpHistoryEntryGroup.Configuration),

    SET_BOLUS(40, R.string.omnipod_cmd_set_bolus, PumpHistoryEntryGroup.Bolus),
    CANCEL_BOLUS(41, R.string.omnipod_cmd_cancel_bolus, PumpHistoryEntryGroup.Bolus),

    CONFIGURE_ALERTS(50, R.string.omnipod_cmd_configure_alerts, PumpHistoryEntryGroup.Alarm),
    ACKNOWLEDGE_ALERTS(51, R.string.omnipod_cmd_acknowledge_alerts, PumpHistoryEntryGroup.Alarm),
    PLAY_TEST_BEEP(52, R.string.omnipod_cmd_play_test_beep, PumpHistoryEntryGroup.Alarm),

    SUSPEND_DELIVERY(60, R.string.omnipod_cmd_suspend_delivery, PumpHistoryEntryGroup.Basal),
    RESUME_DELIVERY(61, R.string.omnipod_cmd_resume_delivery, PumpHistoryEntryGroup.Basal),

    UNKNOWN_ENTRY_TYPE(99, R.string.omnipod_cmd_unknown_entry);

    private final int code;
    private static final Map<Integer, PodHistoryEntryType> instanceMap;

    @StringRes
    private final int resourceId;

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
            return UNKNOWN_ENTRY_TYPE;
        }
    }

    public int getResourceId() {
        return resourceId;
    }
}
