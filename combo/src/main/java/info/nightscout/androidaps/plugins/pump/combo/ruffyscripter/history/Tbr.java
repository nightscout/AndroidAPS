package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history;

import java.util.Date;

public class Tbr extends HistoryRecord {
    /** Duration in minutes */
    public final int duration;
    public final int percent;

    public Tbr(long timestamp, int duration, int percent) {
        super(timestamp);
        this.duration = duration;
        this.percent = percent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tbr tbr = (Tbr) o;

        if (timestamp != tbr.timestamp) return false;
        if (duration != tbr.duration) return false;
        return percent == tbr.percent;
    }

    @Override
    public int hashCode() {
        int result = (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + duration;
        result = 31 * result + percent;
        return result;
    }

    @Override
    public String toString() {
        return "Tbr{" +
                "timestamp=" + timestamp + "(" + new Date(timestamp) + ")" +
                ", duration=" + duration +
                ", percent=" + percent +
                '}';
    }
}
