package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class ServiceCommandNotAvailableException extends AppLayerErrorException {

    public ServiceCommandNotAvailableException(int errorCode) {
        super(errorCode);
    }
}
