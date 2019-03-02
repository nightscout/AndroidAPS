package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class ServiceAlreadyActivatedException extends AppLayerErrorException {

    public ServiceAlreadyActivatedException(int errorCode) {
        super(errorCode);
    }
}
