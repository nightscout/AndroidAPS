package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidPayloadException extends AppLayerErrorException {

    public InvalidPayloadException(int errorCode) {
        super(errorCode);
    }
}
