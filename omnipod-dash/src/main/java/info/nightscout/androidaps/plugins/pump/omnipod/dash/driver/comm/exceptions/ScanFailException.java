package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class ScanFailException extends Exception {
    public ScanFailException() {
    }

    public ScanFailException(int errorCode) {
        super("errorCode" + errorCode);
    }
}
