package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidServicePasswordException extends AppLayerErrorException {

    public InvalidServicePasswordException(int errorCode) {
        super(errorCode);
    }
}
