package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history;

import java.util.Date;

public class Bolus extends HistoryRecord {
    public final double amount;
    public final boolean isValid;

    public Bolus(long timestamp, double amount, boolean isValid) {
        super(timestamp);
        this.amount = amount;
        this.isValid = isValid;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Bolus bolus = (Bolus) o;

        if (timestamp != bolus.timestamp) return false;
        if (isValid != bolus.isValid) return false;
        return Math.abs(bolus.amount - amount) <= 0.01;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (timestamp ^ (timestamp >>> 32));
        temp = Double.doubleToLongBits(amount);
        result = result + (isValid ? 1 : 0);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Bolus{" +
                "timestamp=" + timestamp + " (" + new Date(timestamp) + ")" +
                ", amount=" + amount +
                '}';
    }
}
