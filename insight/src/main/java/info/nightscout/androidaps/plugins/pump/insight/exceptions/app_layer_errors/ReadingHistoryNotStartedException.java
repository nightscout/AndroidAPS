package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

public class ReadingHistoryNotStartedException extends AppLayerErrorException {

    public ReadingHistoryNotStartedException(int errorCode) {
        super(errorCode);
    }
}
