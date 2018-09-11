package info.nightscout.androidaps.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 19.03.2018.
 */

public class Constraint<T extends Comparable> {
    private static Logger log = LoggerFactory.getLogger(L.CONSTRAINTS);

    T value;
    T originalValue;

    List<String> reasons = new ArrayList<>();
    List<String> mostLimiting = new ArrayList<>();

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

    public Constraint<T> set(T value) {
        this.value = value;
        this.originalValue = value;
        if (L.isEnabled(L.CONSTRAINTS))
            log.debug("Setting value " + value);
        return this;
    }

    public Constraint<T> set(T value, String reason, Object from) {
        if (L.isEnabled(L.CONSTRAINTS))
            log.debug("Setting value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
        this.value = value;
        addReason(reason, from);
        addMostLimingReason(reason, from);
        return this;
    }

    public Constraint<T> setIfDifferent(T value, String reason, Object from) {
        if (!this.value.equals(value)) {
            if (L.isEnabled(L.CONSTRAINTS))
                log.debug("Setting because of different value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
            this.value = value;
            addReason(reason, from);
            addMostLimingReason(reason, from);
        }
        return this;
    }

    public Constraint<T> setIfSmaller(T value, String reason, Object from) {
        if (value.compareTo(this.value) < 0) {
            if (L.isEnabled(L.CONSTRAINTS))
                log.debug("Setting because of smaller value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
            this.value = value;
            mostLimiting.clear();
            addMostLimingReason(reason, from);
        }
        if (value.compareTo(this.originalValue) < 0) {
            addReason(reason, from);
        }
        return this;
    }

    public Constraint<T> setIfGreater(T value, String reason, Object from) {
        if (value.compareTo(this.value) > 0) {
            if (L.isEnabled(L.CONSTRAINTS))
                log.debug("Setting because of greater value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]");
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

    public Constraint addReason(String reason, Object from) {
        reasons.add(translateFrom(from) + ": " + reason);
        return this;
    }

    public Constraint addMostLimingReason(String reason, Object from) {
        mostLimiting.add(translateFrom(from) + ": " + reason);
        return this;
    }

    public String getReasons() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String r : reasons) {
            if (count++ != 0) sb.append("\n");
            sb.append(r);
        }
        if (L.isEnabled(L.CONSTRAINTS))
            log.debug("Limiting origial value: " + originalValue + " to " + value + ". Reason: " + sb.toString());
        return sb.toString();
    }

    public List<String> getReasonList() {
        return reasons;
    }

    public String getMostLimitedReasons() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String r : mostLimiting) {
            if (count++ != 0) sb.append("\n");
            sb.append(r);
        }
        if (L.isEnabled(L.CONSTRAINTS))
            log.debug("Limiting origial value: " + originalValue + " to " + value + ". Reason: " + sb.toString());
        return sb.toString();
    }

    public List<String> getMostLimitedReasonList() {
        return mostLimiting;
    }

    public void copyReasons(Constraint<?> another) {
        for (String s : another.getReasonList()) {
            reasons.add(s);
        }
    }
}
