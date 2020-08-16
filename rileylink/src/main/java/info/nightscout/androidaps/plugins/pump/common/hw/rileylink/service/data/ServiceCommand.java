package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data;

import android.os.Bundle;

/**
 * Created by geoff on 6/25/16.
 */
public class ServiceCommand extends ServiceMessage {

    public ServiceCommand() {
        map = new Bundle();
    }


    // commandID is a string that the client can set on the message.
    // The service does not use this value, but passes it back with the result
    // so that the client can identify it.
    public ServiceCommand(String commandName, String commandID) {
        init();
        map.putString("command", commandName);
        map.putString("commandID", commandID);
    }


    public ServiceCommand(Bundle commandBundle) {
        if (commandBundle != null) {
            map = commandBundle;
        } else {
            map = new Bundle();
            init();
            map.putString("command", "(null)");
            map.putString("commandID", "(null");
        }
    }


    @Override
    public void init() {
        map.putString("ServiceMessageType", "ServiceCommand");
    }


    public String getCommandID() {
        return map.getString("commandID");
    }


    public String getCommandName() {
        return map.getString("command");
    }


    public boolean isPumpCommand() {
        switch (getCommandName()) {
            case "FetchPumpHistory":
            case "ReadPumpClock":
            case "RetrieveHistoryPage":
            case "ReadISFProfile":
            case "ReadBolusWizardCarbProfile":
            case "UpdatePumpStatus":
            case "WakeAndTune":
                return true;
            default:
                return false;
        }
    }
}
