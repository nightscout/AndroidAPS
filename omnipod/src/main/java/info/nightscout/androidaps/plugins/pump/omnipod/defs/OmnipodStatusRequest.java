package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum OmnipodStatusRequest {
    ResetState(OmnipodCommandType.ResetPodStatus), //
    AcknowledgeAlerts(OmnipodCommandType.AcknowledgeAlerts), //
    GetPodState(OmnipodCommandType.GetPodStatus), //
    GetPodPulseLog(OmnipodCommandType.GetPodPulseLog);

    private OmnipodCommandType commandType;

    OmnipodStatusRequest(OmnipodCommandType commandType) {
        this.commandType = commandType;
    }


    public OmnipodCommandType getCommandType() {
        return commandType;
    }
}
