package info.nightscout.androidaps.plugins.pump.omnipod.definition;

public interface PodInitReceiver {

    void returnInitTaskStatus(PodInitActionType podInitActionType, boolean isSuccess, String errorMessage);

}
