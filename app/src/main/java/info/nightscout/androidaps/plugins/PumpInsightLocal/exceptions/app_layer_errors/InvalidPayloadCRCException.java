package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidPayloadCRCException extends AppLayerErrorException {

    public InvalidPayloadCRCException(int errorCode) {
        super(errorCode);
    }
}
