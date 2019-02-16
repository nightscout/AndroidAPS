package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class PauseModeNotAllowedException extends AppLayerErrorException {

    public PauseModeNotAllowedException(int errorCode) {
        super(errorCode);
    }
}
