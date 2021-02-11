package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition;

public abstract class AlertTrigger<T> {
    private final T value;

    AlertTrigger(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override public String toString() {
        return "AlertTrigger{" +
                "value=" + value +
                '}';
    }
}
