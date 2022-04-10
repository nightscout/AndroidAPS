package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class StepCountOutOfRangeException extends AppLayerErrorException {

    public StepCountOutOfRangeException(int errorCode) {
        super(errorCode);
    }
}
