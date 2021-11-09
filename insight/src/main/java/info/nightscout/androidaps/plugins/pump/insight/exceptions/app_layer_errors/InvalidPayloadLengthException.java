package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidPayloadLengthException extends AppLayerErrorException {

    public InvalidPayloadLengthException(int errorCode) {
        super(errorCode);
    }
}
