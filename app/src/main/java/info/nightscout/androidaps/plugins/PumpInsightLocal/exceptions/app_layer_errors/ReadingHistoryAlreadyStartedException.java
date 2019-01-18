package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class ReadingHistoryAlreadyStartedException extends AppLayerErrorException {

    public ReadingHistoryAlreadyStartedException(int errorCode) {
        super(errorCode);
    }
}
