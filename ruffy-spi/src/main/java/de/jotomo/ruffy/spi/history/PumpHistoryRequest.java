package de.jotomo.ruffy.spi.history;

/** What data a 'read history' request should return. */
public class PumpHistoryRequest {
    public boolean reservoirLevel;

    /* History to read:
       Either the timestamp of the last known record to fetch all newer records,
       or one of the constants to read no history or all of it.
     */
    public static final long LAST = -2;
    public static final long SKIP = -1;
    public static final long FULL = 0;

    public long bolusHistory = SKIP;
    public long tbrHistory = SKIP;
    public long errorHistory = SKIP;
    public long tddHistory = SKIP;

    public PumpHistoryRequest reservoirLevel(boolean reservoirLevel) {
        this.reservoirLevel = reservoirLevel;
        return this;
    }

    public PumpHistoryRequest bolusHistory(long bolusHistory) {
        this.bolusHistory = bolusHistory;
        return this;
    }

    public PumpHistoryRequest tbrHistory(long tbrHistory) {
        this.tbrHistory = tbrHistory;
        return this;
    }

    public PumpHistoryRequest errorHistory(long errorHistory) {
        this.errorHistory = errorHistory;
        return this;
    }

    public PumpHistoryRequest tddHistory(long tddHistory) {
        this.tddHistory = tddHistory;
        return this;
    }

    @Override
    public String toString() {
        return "PumpHistoryRequest{" +
                "reservoirLevel=" + reservoirLevel +
                ", bolusHistory=" + bolusHistory +
                ", tbrHistory=" + tbrHistory +
                ", errorHistory=" + errorHistory +
                ", tddHistory=" + tddHistory +
                '}';
    }
}
