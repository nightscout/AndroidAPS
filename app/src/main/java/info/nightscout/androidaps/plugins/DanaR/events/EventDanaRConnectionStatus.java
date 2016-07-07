package info.nightscout.androidaps.plugins.DanaR.events;

public class EventDanaRConnectionStatus {
    public boolean sConnecting = false;
    public boolean sConnected = false;
    public int sConnectionAttemptNo =0;

    public EventDanaRConnectionStatus(boolean connecting, boolean connected, int connectionAttemptNo) {
        sConnecting = connecting;
        sConnected = connected;

        if(connectionAttemptNo!=0)
            sConnectionAttemptNo = connectionAttemptNo;
    }

    public EventDanaRConnectionStatus() {

    }
}
