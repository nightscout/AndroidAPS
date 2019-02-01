package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class ConfigMemoryAccessException extends AppLayerErrorException {

    public ConfigMemoryAccessException(int errorCode) {
        super(errorCode);
    }
}
