package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class PumpAlreadyInThatStateException extends AppLayerErrorException {

    public PumpAlreadyInThatStateException(int errorCode) {
        super(errorCode);
    }
}
