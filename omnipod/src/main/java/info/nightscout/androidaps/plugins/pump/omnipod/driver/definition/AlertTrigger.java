package info.nightscout.androidaps.plugins.pump.omnipod.driver.definition;

abstract class AlertTrigger<T> {
    private final T value;

    AlertTrigger(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}
