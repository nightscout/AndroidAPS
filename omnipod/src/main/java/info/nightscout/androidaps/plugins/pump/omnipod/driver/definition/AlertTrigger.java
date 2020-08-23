package info.nightscout.androidaps.plugins.pump.omnipod.driver.definition;

public abstract class AlertTrigger<T> {
    protected T value;

    public AlertTrigger(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}

