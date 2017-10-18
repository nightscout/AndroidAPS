package de.jotomo.ruffy.spi.history;

public class Tbr extends HistoryRecord {
    public final int duration;
    public final int percent;

    public Tbr(long timestamp, int duration, int percent) {
        super(timestamp);
        this.duration = duration;
        this.percent = percent;
    }
}
