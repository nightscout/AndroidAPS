package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidPayloadLengthException extends AppLayerErrorException {

    public InvalidPayloadLengthException(int errorCode) {
        super(errorCode);
    }
}
