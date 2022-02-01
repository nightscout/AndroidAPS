package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidDateParameterException extends AppLayerErrorException {

    public InvalidDateParameterException(int errorCode) {
        super(errorCode);
    }
}
