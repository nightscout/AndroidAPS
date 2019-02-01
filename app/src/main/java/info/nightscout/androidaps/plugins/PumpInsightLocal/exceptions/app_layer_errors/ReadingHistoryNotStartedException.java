package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class ReadingHistoryNotStartedException extends AppLayerErrorException {

    public ReadingHistoryNotStartedException(int errorCode) {
        super(errorCode);
    }
}
