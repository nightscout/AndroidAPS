package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class AlreadyConnectedException extends AppLayerErrorException {

    public AlreadyConnectedException(int errorCode) {
        super(errorCode);
    }
}
