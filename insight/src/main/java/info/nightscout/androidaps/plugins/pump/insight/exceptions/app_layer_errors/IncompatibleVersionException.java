package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class IncompatibleVersionException extends AppLayerErrorException {

    public IncompatibleVersionException(int errorCode) {
        super(errorCode);
    }
}
