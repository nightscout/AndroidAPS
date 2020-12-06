package info.nightscout.androidaps.plugins.pump.omnipod.queue.command;

public enum OmnipodCustomCommandType {
    ACKNOWLEDGE_ALERTS("ACKNOWLEDGE ALERTS"),
    GET_POD_STATUS("GET POD STATUS"),
    READ_PULSE_LOG("READ PULSE LOG"),
    SUSPEND_DELIVERY("SUSPEND DELIVERY"),
    RESUME_DELIVERY("RESUME DELIVERY"),
    DEACTIVATE_POD("DEACTIVATE POD"),
    HANDLE_TIME_CHANGE("HANDLE TIME CHANGE"),
    UPDATE_ALERT_CONFIGURATION("UPDATE ALERT CONFIGURATION"),
    PLAY_TEST_BEEP("PLAY TEST BEEP");

    private final String description;

    OmnipodCustomCommandType(String description) {
        this.description = description;
    }

    String getDescription() {
        return description;
    }
}
