package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public class OmnipodEvent {
    public enum OmnipodEventType {
        CONNECTED,
        ALREADY_CONNECTED,
        FAILED_TO_CONNECT,
        DISCONNECTED,
        COMMAND_SENT,
        GOT_POD_VERSION,
        SET_UNIQUE_ID,
        PRIMED_PUMP,
        FINISHED_ACTIVATION_1,
        PROGRAMMED_BASAL,
        PROGRAMMED_ALERTS,
        SET_BEEPS,
        INSERTED_CANNULA,
        FINISHED_ACTIVATION_2,
        PROGRAMMED_TEMP_BASAL,
        STARTED_BOLUS,
        STOPPED_DELIVERY,
        SILENCED_ALERTS,
        DEACTIVATED,
        COMMAND_SENDING,
    }
}
