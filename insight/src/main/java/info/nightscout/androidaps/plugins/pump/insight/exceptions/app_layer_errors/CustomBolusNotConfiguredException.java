package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class CustomBolusNotConfiguredException extends AppLayerErrorException {

    public CustomBolusNotConfiguredException(int errorCode) {
        super(errorCode);
    }
}
