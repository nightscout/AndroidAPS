package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class WrongStateException extends AppLayerErrorException {

    public WrongStateException(int errorCode) {
        super(errorCode);
    }
}
