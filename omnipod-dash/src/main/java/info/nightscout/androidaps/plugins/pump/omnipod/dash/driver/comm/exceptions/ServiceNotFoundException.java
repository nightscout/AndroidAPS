package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class ServiceNotFoundException extends FailedToConnectException {
    public ServiceNotFoundException(String serviceUuid) {
        super("service not found: " + serviceUuid);
    }
}
