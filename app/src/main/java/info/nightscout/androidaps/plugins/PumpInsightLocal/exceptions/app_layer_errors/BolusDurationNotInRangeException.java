package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class BolusDurationNotInRangeException extends AppLayerErrorException {

    public BolusDurationNotInRangeException(int errorCode) {
        super(errorCode);
    }
}
