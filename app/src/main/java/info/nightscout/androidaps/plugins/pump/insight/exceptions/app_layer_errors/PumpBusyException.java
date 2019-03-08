package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class PumpBusyException extends AppLayerErrorException {

    public PumpBusyException(int errorCode) {
        super(errorCode);
    }
}
