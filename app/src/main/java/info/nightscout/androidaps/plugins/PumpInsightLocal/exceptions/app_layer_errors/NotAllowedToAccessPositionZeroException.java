package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class NotAllowedToAccessPositionZeroException extends AppLayerErrorException {

    public NotAllowedToAccessPositionZeroException(int errorCode) {
        super(errorCode);
    }
}
