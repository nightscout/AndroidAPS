package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class FailedToConnectException extends Exception {
    public FailedToConnectException() {
        super();
    }

    public FailedToConnectException(String message) {
        super(message);
    }
}
