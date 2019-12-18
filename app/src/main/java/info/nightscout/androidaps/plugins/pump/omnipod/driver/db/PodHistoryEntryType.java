package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by andy on 24.11.2019
 */
public enum PodHistoryEntryType {

    PairAndPrime(1),
    FillCannulaSetBasalProfile(2),
    DeactivatePod(3),
    ResetPodState(4),

    SetTemporaryBasal(10),
    CancelTemporaryBasal(11),

    SetBasalSchedule(20),

    GetPodStatus(30),
    GetPodInfo(31),
    SetTime(32),

    SetBolus(40),
    CancelBolus(41),

    ConfigureAlerts(50),
    AcknowledgeAlerts(51),

    SuspendDelivery(60),
    ResumeDelivery(61)
    ;

    private int code;
    private static Map<Integer, PodHistoryEntryType> instanceMap;


    static {
        instanceMap = new HashMap<>();

        for (PodHistoryEntryType value : values()) {
            instanceMap.put(value.code, value);
        }
    }


    PodHistoryEntryType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
