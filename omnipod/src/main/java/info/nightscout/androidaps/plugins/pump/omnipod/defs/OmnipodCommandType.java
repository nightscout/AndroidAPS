package info.nightscout.androidaps.plugins.pump.omnipod.defs;

/**
 * Created by andy on 4.8.2019
 */
public enum OmnipodCommandType {
    PairAndPrimePod, // First step of Pod activation
    FillCanulaAndSetBasalProfile, // Second step of Pod activation
    DeactivatePod, //
    SetBasalProfile, //
    SetBolus, //
    CancelBolus, //
    SetTemporaryBasal, //
    CancelTemporaryBasal, //
    ResetPodStatus, //
    GetPodStatus, //
    SetTime, //
    AcknowledgeAlerts, //
    GetPodPulseLog;
}
