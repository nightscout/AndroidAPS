package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

/**
 * Created by andy on 24.11.2019
 */
public enum PodDbEntryType {

    PairAndPrime(1),
    InsertCannula(2),
    DeactivatePod(3),
    ResetPodState(4),

    SetTemporaryBasal(10),
    CancelTemporaryBasal(11),

    SetBasalSchedule(20),

    GetPodStatus(30),
    GetPodInfo(31),

    SetBolus(40),
    CancelBolus(41),

    ConfigureAlerts(50),
    AcknowledgeAlerts(51),

    SuspendDelivery(60),
    ResumeDelivery(61)
    ;

    private int code;

    PodDbEntryType(int code) {

        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
