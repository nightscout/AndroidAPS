package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class InvalidConfigBlockCRCException extends AppLayerErrorException {

    public InvalidConfigBlockCRCException(int errorCode) {
        super(errorCode);
    }
}
