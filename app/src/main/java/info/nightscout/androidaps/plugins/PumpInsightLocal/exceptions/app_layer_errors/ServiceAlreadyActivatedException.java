package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class ServiceAlreadyActivatedException extends AppLayerErrorException {

    public ServiceAlreadyActivatedException(int errorCode) {
        super(errorCode);
    }
}
