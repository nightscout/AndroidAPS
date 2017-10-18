package de.jotomo.ruffy.spi.history;

import android.support.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public class PumpHistory {
    public int reservoirLevel = -1;
    @NonNull
    public List<Bolus> bolusHistory = Collections.emptyList();
    @NonNull
    public List<Tbr> tbrHistory = Collections.emptyList();
    @NonNull
    public List<Error> errorHistory = Collections.emptyList();
    @NonNull
    public List<Tdd> tddHistory = Collections.emptyList();

    public PumpHistory reservoirLevel(int reservoirLevel) {
        this.reservoirLevel = reservoirLevel
        ;
        return this;
    }

    public PumpHistory bolusHistory(List<Bolus> bolusHistory) {
        this.bolusHistory = bolusHistory;
        return this;
    }

    public PumpHistory tbrHistory(List<Tbr> tbrHistory) {
        this.tbrHistory = tbrHistory;
        return this;
    }

    public PumpHistory errorHistory(List<Error> errorHistory) {
        this.errorHistory = errorHistory;
        return this;
    }

    @Override
    public String toString() {
        return "PumpHistory{" +
                "reservoirLevel=" + reservoirLevel +
                ", bolusHistory=" + bolusHistory +
                ", tbrHistory=" + tbrHistory +
                ", errorHistory=" + errorHistory +
                ", tddHistory=" + tddHistory +
                '}';
    }

    public PumpHistory tddHistory(List<Tdd> tddHistory) {
        this.tddHistory = tddHistory;
        return this;
    }
}
