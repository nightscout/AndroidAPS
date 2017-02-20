package info.nightscout.androidaps.events;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 19.02.2017.
 */

public class EventPumpStatusChanged {
    public static final int CONNECTING = 0;
    public static final int CONNECTED = 1;
    public static final int PERFORMING = 2;
    public static final int DISCONNECTING = 3;
    public static final int DISCONNECTED = 4;

    public int sStatus = DISCONNECTED;
    public int sSecondsElapsed = 0;
    public String sPerfomingAction = "";

    public EventPumpStatusChanged(int status) {
        sStatus = status;
        sSecondsElapsed = 0;
    }

    public EventPumpStatusChanged(int status, int secondsElapsed) {
        sStatus = status;
        sSecondsElapsed = secondsElapsed;
    }

    public EventPumpStatusChanged(String action) {
        sStatus = PERFORMING;
        sSecondsElapsed = 0;
        sPerfomingAction = action;
    }

    public String textStatus() {
        if (sStatus == CONNECTING)
            return String.format(MainApp.sResources.getString(R.string.danar_history_connectingfor), sSecondsElapsed);
        else if (sStatus == CONNECTED)
            return MainApp.sResources.getString(R.string.connected);
        else if (sStatus == PERFORMING)
            return sPerfomingAction;
        else if (sStatus == DISCONNECTING)
            return MainApp.sResources.getString(R.string.disconnecting);
        else if (sStatus == DISCONNECTED)
            return "";
        return "";
    }
}
