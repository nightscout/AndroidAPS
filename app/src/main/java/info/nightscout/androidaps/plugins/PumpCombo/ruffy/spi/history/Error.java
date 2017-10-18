package info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.history;

public class Error extends HistoryRecord {
    /** Code is an E for error or W for warning, followed by a single digit, e.g. W7 (TBR cancelled). */
    public final String code;
    /** Error message, in the language configured on the pump. */
    public final String message;

    public Error(long timestamp, String code, String message) {
        super(timestamp);
        this.code = code;
        this.message = message;
    }
}
