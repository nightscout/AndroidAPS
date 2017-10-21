package de.jotomo.ruffy.spi.history;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PumpHistory {
    public int reservoirLevel = -1;
    @NonNull
    public List<Bolus> bolusHistory = new ArrayList<>();
    @NonNull
    public List<Tbr> tbrHistory = new ArrayList<>();
    @NonNull
    public List<Error> errorHistory = new ArrayList<>();
    @NonNull
    public List<Tdd> tddHistory = new ArrayList<>();

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

    public PumpHistory tddHistory(List<Tdd> tddHistory) {
        this.tddHistory = tddHistory;
        return this;
    }

    @Override
    public String toString() {
        return "PumpHistory{" +
                "reservoirLevel=" + reservoirLevel +
                ", bolusHistory=" + bolusHistory.size() +
                ", tbrHistory=" + tbrHistory.size() +
                ", errorHistory=" + errorHistory.size() +
                ", tddHistory=" + tddHistory.size() +
                '}';
    }
}
