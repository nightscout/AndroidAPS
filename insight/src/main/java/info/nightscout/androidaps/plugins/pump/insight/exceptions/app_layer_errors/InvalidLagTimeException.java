package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidLagTimeException extends AppLayerErrorException {

    public InvalidLagTimeException(int errorCode) {
        super(errorCode);
    }
}
