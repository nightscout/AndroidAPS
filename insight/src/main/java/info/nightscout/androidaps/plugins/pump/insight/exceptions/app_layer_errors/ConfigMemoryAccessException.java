package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class ConfigMemoryAccessException extends AppLayerErrorException {

    public ConfigMemoryAccessException(int errorCode) {
        super(errorCode);
    }
}
