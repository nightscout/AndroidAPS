package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class BolusAmountNotInRangeException extends AppLayerErrorException {

    public BolusAmountNotInRangeException(int errorCode) {
        super(errorCode);
    }
}
