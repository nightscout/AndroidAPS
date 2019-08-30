package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public interface PodInitReceiver {

    void returnInitTaskStatus(PodInitActionType podInitActionType, boolean isSuccess, String errorMessage);

}
