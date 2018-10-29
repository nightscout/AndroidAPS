package info.nightscout.androidaps.plugins.general.automation.triggers;

public class DummyTrigger extends Trigger {
    private boolean result;

    public DummyTrigger() {
        this.result = false;
    }

    public DummyTrigger(boolean result) {
        this.result = result;
    }

    @Override
    public boolean shouldRun() {
        return result;
    }

    @Override
    public String toJSON() { return null; }

    @Override
    Trigger fromJSON(String data) {
        return null;
    }

    @Override
    public int friendlyName() {
        return 0;
    }

    @Override
    public String friendlyDescription() {
        return null;
    }

    @Override
    public Trigger duplicate() { return new DummyTrigger(result); }
}
