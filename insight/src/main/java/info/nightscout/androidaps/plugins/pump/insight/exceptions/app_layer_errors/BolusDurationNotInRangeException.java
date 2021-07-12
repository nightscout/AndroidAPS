package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class BolusDurationNotInRangeException extends AppLayerErrorException {

    public BolusDurationNotInRangeException(int errorCode) {
        super(errorCode);
    }
}
