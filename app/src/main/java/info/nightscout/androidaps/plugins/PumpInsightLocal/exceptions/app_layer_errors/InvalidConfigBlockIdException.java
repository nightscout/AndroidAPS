package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidConfigBlockIdException extends AppLayerErrorException {

    public InvalidConfigBlockIdException(int errorCode) {
        super(errorCode);
    }
}
