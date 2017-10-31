package de.jotomo.ruffy.spi.history;

public class Bolus extends HistoryRecord {
    public final double amount;

    public Bolus(long timestamp, double amount) {
        super(timestamp);
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Bolus bolus = (Bolus) o;

        if (timestamp != bolus.timestamp) return false;
        return Double.compare(bolus.amount, amount) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (timestamp ^ (timestamp >>> 32));
        temp = Double.doubleToLongBits(amount);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Bolus{" +
                "timestamp=" + timestamp +
                ", amount=" + amount +
                '}';
    }
}
