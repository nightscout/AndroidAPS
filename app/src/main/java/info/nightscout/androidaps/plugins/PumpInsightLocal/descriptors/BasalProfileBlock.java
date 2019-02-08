package info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors;

public class BasalProfileBlock {

    private int duration;
    private double basalAmount;

    public int getDuration() {
        return this.duration;
    }

    public double getBasalAmount() {
        return this.basalAmount;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setBasalAmount(double basalAmount) {
        this.basalAmount = basalAmount;
    }
}
