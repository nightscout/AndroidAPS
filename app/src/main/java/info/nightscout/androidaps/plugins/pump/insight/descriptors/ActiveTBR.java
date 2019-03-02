package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class ActiveTBR {

    private int percentage;
    private int remainingDuration;
    private int initialDuration;

    public int getPercentage() {
        return this.percentage;
    }

    public int getRemainingDuration() {
        return this.remainingDuration;
    }

    public int getInitialDuration() {
        return this.initialDuration;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public void setRemainingDuration(int remainingDuration) {
        this.remainingDuration = remainingDuration;
    }

    public void setInitialDuration(int initialDuration) {
        this.initialDuration = initialDuration;
    }
}
