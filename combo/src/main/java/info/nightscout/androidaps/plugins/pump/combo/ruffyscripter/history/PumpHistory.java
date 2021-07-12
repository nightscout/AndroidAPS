package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** History data as read from the pump's My Data menu.
 * Records are ordered from newest to oldest, so the first record is always the newest. */
public class PumpHistory {
    @NonNull
    public List<Bolus> bolusHistory = new ArrayList<>();
    @NonNull
    public List<Tbr> tbrHistory = new ArrayList<>();
    @NonNull
    public List<PumpAlert> pumpAlertHistory = new LinkedList<>();
    @NonNull
    public List<Tdd> tddHistory = new ArrayList<>();

    public PumpHistory bolusHistory(List<Bolus> bolusHistory) {
        this.bolusHistory = bolusHistory;
        return this;
    }

    public PumpHistory tbrHistory(List<Tbr> tbrHistory) {
        this.tbrHistory = tbrHistory;
        return this;
    }

    public PumpHistory pumpErrorHistory(List<PumpAlert> pumpAlertHistory) {
        this.pumpAlertHistory = pumpAlertHistory;
        return this;
    }

    public PumpHistory tddHistory(List<Tdd> tddHistory) {
        this.tddHistory = tddHistory;
        return this;
    }

    @Override
    public String toString() {
        return "PumpHistory{" +
                "bolusHistory=" + bolusHistory.size() +
                ", tbrHistory=" + tbrHistory.size() +
                ", pumpAlertHistory=" + pumpAlertHistory.size() +
                ", tddHistory=" + tddHistory.size() +
                '}';
    }
}
