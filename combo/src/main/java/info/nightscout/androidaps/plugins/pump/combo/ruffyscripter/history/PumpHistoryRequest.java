package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history;

import java.util.Date;

/** What data a 'read history' request should return. */
public class PumpHistoryRequest {
    /* History to read:
       Either the timestamp of the last known record or one of the constants to read no history
       or all of it. When a timestamp is provided all newer records and records matching the
       timestamp are returned. Returning all records equal to the timestamp ensures a record
       with a duplicate timestamp is also detected as a new record.
     */
    public static final long LAST = -2;
    public static final long SKIP = -1;
    public static final long FULL = 0;

    public long bolusHistory = SKIP;
    public long tbrHistory = SKIP;
    public long pumpErrorHistory = SKIP;
    public long tddHistory = SKIP;

    public PumpHistoryRequest bolusHistory(long bolusHistory) {
        this.bolusHistory = bolusHistory;
        return this;
    }

    public PumpHistoryRequest tbrHistory(long tbrHistory) {
        this.tbrHistory = tbrHistory;
        return this;
    }

    public PumpHistoryRequest pumpErrorHistory(long pumpErrorHistory) {
        this.pumpErrorHistory = pumpErrorHistory;
        return this;
    }

    public PumpHistoryRequest tddHistory(long tddHistory) {
        this.tddHistory = tddHistory;
        return this;
    }

    @Override
    public String toString() {
        return "PumpHistoryRequest{" +
                "bolusHistory=" + bolusHistory + (bolusHistory > 0 ? ("(" + new Date(bolusHistory) + ")") : "") +
                ", tbrHistory=" + tbrHistory + (tbrHistory > 0 ? ("(" + new Date(tbrHistory) + ")") : "") +
                ", pumpAlertHistory=" + pumpErrorHistory + (pumpErrorHistory > 0 ? ("(" + new Date(pumpErrorHistory) + ")") : "") +
                ", tddHistory=" + tddHistory + (tddHistory > 0 ? ("(" + new Date(tddHistory) + ")") : "") +
                '}';
    }
}
