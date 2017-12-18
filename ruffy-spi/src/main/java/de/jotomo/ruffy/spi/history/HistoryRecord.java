package de.jotomo.ruffy.spi.history;

public abstract class HistoryRecord {
    public final long timestamp;

    protected HistoryRecord(long timestamp) {
        this.timestamp = timestamp;
    }
}
