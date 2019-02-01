package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class NotConnectedException extends AppLayerErrorException {

    public NotConnectedException(int errorCode) {
        super(errorCode);
    }
}
