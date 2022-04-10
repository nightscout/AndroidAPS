package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class PositionProtectedException extends AppLayerErrorException {

    public PositionProtectedException(int errorCode) {
        super(errorCode);
    }
}
