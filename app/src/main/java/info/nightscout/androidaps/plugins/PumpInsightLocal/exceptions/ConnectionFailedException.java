package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions;

public class ConnectionFailedException extends InsightException {

    private long durationOfConnectionAttempt;

    public ConnectionFailedException(long durationOfConnectionAttempt) {
        this.durationOfConnectionAttempt = durationOfConnectionAttempt;
    }

    public long getDurationOfConnectionAttempt() {
        return durationOfConnectionAttempt;
    }
}
