package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class IncompatibleVersionException extends AppLayerErrorException {

    public IncompatibleVersionException(int errorCode) {
        super(errorCode);
    }
}
