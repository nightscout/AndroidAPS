package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class RunModeNotAllowedException extends AppLayerErrorException {

    public RunModeNotAllowedException(int errorCode) {
        super(errorCode);
    }
}
