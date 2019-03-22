package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidConfigBlockIdException extends AppLayerErrorException {

    public InvalidConfigBlockIdException(int errorCode) {
        super(errorCode);
    }
}
