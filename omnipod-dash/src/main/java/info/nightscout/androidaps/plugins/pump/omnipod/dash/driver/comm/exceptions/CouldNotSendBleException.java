package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class CouldNotSendBleException extends Exception {
    public CouldNotSendBleException(String msg) {
        super(msg);
    }
}
