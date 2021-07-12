package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidServicePasswordException extends AppLayerErrorException {

    public InvalidServicePasswordException(int errorCode) {
        super(errorCode);
    }
}
