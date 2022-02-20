package info.nightscout.androidaps.plugins.pump.combo.data;

public class ErrorState {

    private Exception exception;
    private long timeInMillis;


    public void setException(Exception exception) {
        this.exception = exception;
    }


    public void setTimeInMillis(long timeInMillis) {
        this.timeInMillis = timeInMillis;
    }
}
