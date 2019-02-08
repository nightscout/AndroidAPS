package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class UnknownAppLayerErrorCodeException extends AppLayerErrorException {

    public UnknownAppLayerErrorCodeException(int errorCode) {
        super(errorCode);
    }
}
