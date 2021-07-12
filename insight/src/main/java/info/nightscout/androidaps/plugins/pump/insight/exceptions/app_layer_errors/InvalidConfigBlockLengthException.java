package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidConfigBlockLengthException extends AppLayerErrorException {

    public InvalidConfigBlockLengthException(int errorCode) {
        super(errorCode);
    }
}
