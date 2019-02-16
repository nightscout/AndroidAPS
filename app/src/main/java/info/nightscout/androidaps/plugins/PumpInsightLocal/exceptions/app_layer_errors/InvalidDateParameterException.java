package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidDateParameterException extends AppLayerErrorException {

    public InvalidDateParameterException(int errorCode) {
        super(errorCode);
    }
}
