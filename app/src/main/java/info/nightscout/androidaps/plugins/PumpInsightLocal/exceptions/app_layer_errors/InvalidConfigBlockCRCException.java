package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class InvalidConfigBlockCRCException extends AppLayerErrorException {

    public InvalidConfigBlockCRCException(int errorCode) {
        super(errorCode);
    }
}
