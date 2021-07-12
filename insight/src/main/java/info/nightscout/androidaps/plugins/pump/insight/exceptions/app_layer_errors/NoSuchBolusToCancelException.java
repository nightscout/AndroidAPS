package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class NoSuchBolusToCancelException extends AppLayerErrorException {

    public NoSuchBolusToCancelException(int errorCode) {
        super(errorCode);
    }
}
