package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class UnknownServiceException extends AppLayerErrorException {

    public UnknownServiceException(int errorCode) {
        super(errorCode);
    }
}
