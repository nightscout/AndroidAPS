package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidParameterTypeException extends AppLayerErrorException {

    public InvalidParameterTypeException(int errorCode) {
        super(errorCode);
    }
}
