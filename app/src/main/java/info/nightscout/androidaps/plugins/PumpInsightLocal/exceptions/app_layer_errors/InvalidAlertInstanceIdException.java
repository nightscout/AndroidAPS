package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidAlertInstanceIdException extends AppLayerErrorException {

    public InvalidAlertInstanceIdException(int errorCode) {
        super(errorCode);
    }
}
