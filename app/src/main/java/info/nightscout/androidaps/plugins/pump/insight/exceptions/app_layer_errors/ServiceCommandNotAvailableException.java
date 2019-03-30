package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class ServiceCommandNotAvailableException extends AppLayerErrorException {

    public ServiceCommandNotAvailableException(int errorCode) {
        super(errorCode);
    }
}
