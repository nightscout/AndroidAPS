package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history;

import java.util.Date;

/** Total daily dosage; amount of insulin delivered over a full day. */
public class Tdd extends HistoryRecord {
    public final double total;

    public Tdd(long timestamp, double total) {
        super(timestamp);
        this.total = total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tdd tdd = (Tdd) o;

        if (timestamp != tdd.timestamp) return false;
        return tdd.total != total;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (timestamp ^ (timestamp >>> 32));
        temp = Double.doubleToLongBits(total);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Tdd{" +
                "timestamp=" + timestamp + "(" + new Date(timestamp) + ")" +
                ", total=" + total +
                '}';
    }
}
