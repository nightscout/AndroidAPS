package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import java.util.ArrayList;
import java.util.List;

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

    public AlertSet(AlertSet alertSet) {
        this(alertSet == null ? new ArrayList<>() : alertSet.getAlertSlots());
    }

    public AlertSet(List<AlertSlot> alertSlots) {
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

    @Override
    public String toString() {
        return "AlertSet{" +
                "alertSlots=" + alertSlots +
                '}';
    }
}