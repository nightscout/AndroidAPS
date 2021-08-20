package info.nightscout.androidaps.interfaces;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

/**
 * Created by mike on 19.03.2018.
 */

public class Constraint<T extends Comparable<T>> {
    private T value;
    private T originalValue;

    private final List<String> reasons = new ArrayList<>();
    private final List<String> mostLimiting = new ArrayList<>();

    public Constraint(T value) {
        this.value = value;
        this.originalValue = value;
    }

    public T value() {
        return value;
    }

    public T originalValue() {
        return originalValue;
    }

    public Constraint<T> set(AAPSLogger aapsLogger, T value) {
        this.value = value;
        this.originalValue = value;
        aapsLogger.debug(LTag.CONSTRAINTS, "Setting value " + value);
        return this;
    }

    public Constraint<T> set(AAPSLogger aapsLogger, T value, String reason, Object from) {
        aapsLogger.debug(LTag.CONSTRAINTS, "Setting value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
        this.value = value;
        addReason(reason, from);
        addMostLimingReason(reason, from);
        return this;
    }

    public Constraint<T> setIfDifferent(AAPSLogger aapsLogger, T value, String reason, Object from) {
        if (!this.value.equals(value)) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of different value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
            this.value = value;
            addReason(reason, from);
            addMostLimingReason(reason, from);
        }
        return this;
    }

    public Constraint<T> setIfSmaller(AAPSLogger aapsLogger, T value, String reason, Object from) {
        if (value.compareTo(this.value) < 0) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of smaller value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
            this.value = value;
            mostLimiting.clear();
            addMostLimingReason(reason, from);
        }
        if (value.compareTo(this.originalValue) < 0) {
            addReason(reason, from);
        }
        return this;
    }

    public Constraint<T> setIfGreater(AAPSLogger aapsLogger, T value, String reason, Object from) {
        if (value.compareTo(this.value) > 0) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of greater value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
            this.value = value;
            mostLimiting.clear();
            addMostLimingReason(reason, from);
        }
        if (value.compareTo(this.originalValue) > 0) {
            addReason(reason, from);
        }
        return this;
    }

    private String translateFrom(Object from) {
        return from.getClass().getSimpleName().replace("Plugin", "");
    }

    public void addReason(String reason, Object from) {
        reasons.add(translateFrom(from) + ": " + reason);
    }

    private void addMostLimingReason(String reason, Object from) {
        mostLimiting.add(translateFrom(from) + ": " + reason);
    }

    public String getReasons(AAPSLogger aapsLogger) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String r : reasons) {
            if (count++ != 0) sb.append("\n");
            sb.append(r);
        }
        aapsLogger.debug(LTag.CONSTRAINTS, "Limiting original value: " + originalValue + " to " + value + ". Reason: " + sb.toString());
        return sb.toString();
    }

    public List<String> getReasonList() {
        return reasons;
    }

    public String getMostLimitedReasons(AAPSLogger aapsLogger) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String r : mostLimiting) {
            if (count++ != 0) sb.append("\n");
            sb.append(r);
        }
        aapsLogger.debug(LTag.CONSTRAINTS, "Limiting original value: " + originalValue + " to " + value + ". Reason: " + sb.toString());
        return sb.toString();
    }

    public List<String> getMostLimitedReasonList() {
        return mostLimiting;
    }

    public void copyReasons(Constraint<?> another) {
        reasons.addAll(another.getReasonList());
    }
}
