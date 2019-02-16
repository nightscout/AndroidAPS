package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class WriteSessionClosedException extends AppLayerErrorException {

    public WriteSessionClosedException(int errorCode) {
        super(errorCode);
    }
}
