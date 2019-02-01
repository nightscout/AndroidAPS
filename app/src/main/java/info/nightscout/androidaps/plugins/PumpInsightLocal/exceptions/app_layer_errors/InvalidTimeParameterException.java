package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidTimeParameterException extends AppLayerErrorException {

    public InvalidTimeParameterException(int errorCode) {
        super(errorCode);
    }
}
