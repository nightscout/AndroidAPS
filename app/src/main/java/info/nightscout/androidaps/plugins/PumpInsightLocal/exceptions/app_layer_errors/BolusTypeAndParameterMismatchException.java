package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class BolusTypeAndParameterMismatchException extends AppLayerErrorException {

    public BolusTypeAndParameterMismatchException(int errorCode) {
        super(errorCode);
    }
}
