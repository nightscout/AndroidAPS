package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class UnknownCommandException extends AppLayerErrorException {

    public UnknownCommandException(int errorCode) {
        super(errorCode);
    }
}
