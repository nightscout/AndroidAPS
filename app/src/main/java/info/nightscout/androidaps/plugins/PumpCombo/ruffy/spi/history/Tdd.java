package info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.history;

/** Total daily dosage; amount of insulin delivered over a full day. */
public class Tdd extends HistoryRecord {
    public final double total;

    public Tdd(long timestamp, double total) {
        super(timestamp);
        this.total = total;
    }
}
