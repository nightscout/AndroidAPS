package app.aaps.pump.omnipod.eros.driver.definition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AlertSet {
    private final List<AlertSlot> alertSlots;

    public AlertSet(byte rawValue) {
        alertSlots = new ArrayList<>();
        for (AlertSlot alertSlot : AlertSlot.values()) {
            if ((alertSlot.getBitMaskValue() & rawValue) != 0) {
                alertSlots.add(alertSlot);
            }
        }
    }

    public AlertSet(@Nullable AlertSet alertSet) {
        this(alertSet == null ? new ArrayList<>() : alertSet.getAlertSlots());
    }

    public AlertSet(@Nullable List<AlertSlot> alertSlots) {
        this.alertSlots = alertSlots == null ? new ArrayList<>() : new ArrayList<>(alertSlots);
    }

    public List<AlertSlot> getAlertSlots() {
        return new ArrayList<>(alertSlots);
    }

    public int size() {
        return alertSlots.size();
    }

    public byte getRawValue() {
        byte value = 0;
        for (AlertSlot alertSlot : alertSlots) {
            value |= alertSlot.getBitMaskValue();
        }
        return value;
    }

    @Override public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertSet alertSet = (AlertSet) o;
        return alertSlots.equals(alertSet.alertSlots);
    }

    @Override public int hashCode() {
        return Objects.hash(alertSlots);
    }

    @NonNull @Override
    public String toString() {
        return "AlertSet{" +
                "alertSlots=" + alertSlots +
                '}';
    }
}