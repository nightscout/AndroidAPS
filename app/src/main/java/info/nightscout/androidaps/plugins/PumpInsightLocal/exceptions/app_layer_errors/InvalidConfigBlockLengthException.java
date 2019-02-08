package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidConfigBlockLengthException extends AppLayerErrorException {

    public InvalidConfigBlockLengthException(int errorCode) {
        super(errorCode);
    }
}
