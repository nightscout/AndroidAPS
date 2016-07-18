package info.nightscout.androidaps.plugins.DanaR.events;

public class EventDanaRConnectionStatus {
    public static final int CONNECTING = 0;
    public static final int CONNECTED = 1;
    public static final int DISCONNECTED = 2;

    public int sStatus = DISCONNECTED;
    public int sSecondsElapsed = 0;

    public EventDanaRConnectionStatus(int status, int secondsElapsed) {
        sStatus = status;
        sSecondsElapsed = secondsElapsed;
    }
}
