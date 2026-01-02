package app.aaps.pump.omnipod.eros.driver.definition;

import androidx.annotation.NonNull;

public abstract class AlertTrigger<T> {
    private final T value;

    AlertTrigger(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @NonNull @Override public String toString() {
        return "AlertTrigger{" +
                "value=" + value +
                '}';
    }
}
