package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class ServiceNotActivatedException extends AppLayerErrorException {

    public ServiceNotActivatedException(int errorCode) {
        super(errorCode);
    }
}
