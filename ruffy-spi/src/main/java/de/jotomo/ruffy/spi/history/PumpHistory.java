package de.jotomo.ruffy.spi.history;

import android.support.annotation.NonNull;

import java.util.List;
import java.util.Objects;

public class PumpHistory {
    public final int reservoirLevel;
    @NonNull
    public final List<Bolus> bolusHistory;
    @NonNull
    public final List<Tbr> tbrHistory;
    @NonNull
    public final List<java.lang.Error> errorHistory;
    @NonNull
    public final List<Tdd> tddHistory;

    public PumpHistory(int reservoirLevel, List<Bolus> bolusHistory, List<Tbr> tbrHistory, List<java.lang.Error> errorHistory, List<Tdd> tddHistory) {
        this.reservoirLevel = reservoirLevel;
        this.bolusHistory = Objects.requireNonNull(bolusHistory);
        this.tbrHistory = Objects.requireNonNull(tbrHistory);
        this.errorHistory = Objects.requireNonNull(errorHistory);
        this.tddHistory = Objects.requireNonNull(tddHistory);
    }
}
