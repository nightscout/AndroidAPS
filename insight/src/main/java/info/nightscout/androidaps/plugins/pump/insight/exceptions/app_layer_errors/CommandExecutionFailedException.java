package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class CommandExecutionFailedException extends AppLayerErrorException {

    public CommandExecutionFailedException(int errorCode) {
        super(errorCode);
    }
}
