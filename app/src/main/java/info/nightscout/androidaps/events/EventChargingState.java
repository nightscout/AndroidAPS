package info.nightscout.androidaps.events;

public class EventChargingState {

    boolean isCharging = false;
    boolean isPlugged = false;

    public EventChargingState() {}

    public EventChargingState(boolean isCharging, boolean isPlugged) {
        this.isCharging = isCharging;
        this.isPlugged = isPlugged;
    }

}
