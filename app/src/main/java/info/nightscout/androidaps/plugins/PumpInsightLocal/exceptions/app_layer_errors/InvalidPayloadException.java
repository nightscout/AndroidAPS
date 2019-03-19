package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidPayloadException extends AppLayerErrorException {

    public InvalidPayloadException(int errorCode) {
        super(errorCode);
    }
}
