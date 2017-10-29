package de.jotomo.ruffy.spi.history;

public class PumpError extends HistoryRecord {
    /** Code is an E for error or W for warning, followed by a single digit, e.g. W7 (TBR cancelled). */
//    public final String code;
    public final Integer warningCode;


    public final Integer errorCode;
    /** Error message, in the language configured on the pump. */
    public final String message;

    public PumpError(long timestamp, Integer warningCode, Integer errorCode, String message) {
        super(timestamp);
        this.warningCode = warningCode;
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    public String toString() {
        return "PumpError{" +
                "timestamp=" + timestamp +
                ", warningCode=" + warningCode +
                ", errorCode=" + errorCode +
                ", message='" + message + '\'' +
                '}';
    }
}
