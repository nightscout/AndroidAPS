package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class WriteSessionAlreadyOpenException extends AppLayerErrorException {

    public WriteSessionAlreadyOpenException(int errorCode) {
        super(errorCode);
    }
}
