package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class CommandExecutionFailedException extends AppLayerErrorException {

    public CommandExecutionFailedException(int errorCode) {
        super(errorCode);
    }
}
