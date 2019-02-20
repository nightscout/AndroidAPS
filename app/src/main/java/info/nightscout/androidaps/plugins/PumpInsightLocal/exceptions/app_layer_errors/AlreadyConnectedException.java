package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class AlreadyConnectedException extends AppLayerErrorException {

    public AlreadyConnectedException(int errorCode) {
        super(errorCode);
    }
}
