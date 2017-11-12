package de.jotomo.ruffy.spi.history;

import java.util.Date;

public class PumpError extends HistoryRecord {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PumpError pumpError = (PumpError) o;

        if (timestamp != pumpError.timestamp) return false;
        if (warningCode != null ? !warningCode.equals(pumpError.warningCode) : pumpError.warningCode != null)
            return false;
        if (errorCode != null ? !errorCode.equals(pumpError.errorCode) : pumpError.errorCode != null)
            return false;
        return message != null ? message.equals(pumpError.message) : pumpError.message == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + (warningCode != null ? warningCode.hashCode() : 0);
        result = 31 * result + (errorCode != null ? errorCode.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PumpError{" +
                "timestamp=" + timestamp + "(" + new Date(timestamp) + ")" +
                ", warningCode=" + warningCode +
                ", errorCode=" + errorCode +
                ", message='" + message + '\'' +
                '}';
    }
}
