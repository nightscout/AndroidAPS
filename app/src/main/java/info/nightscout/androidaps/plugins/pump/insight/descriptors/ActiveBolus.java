package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class ActiveBolus {

    private int bolusID;
    private BolusType bolusType;
    private double initialAmount;
    private double remainingAmount;
    private int remainingDuration;

    public int getBolusID() {
        return this.bolusID;
    }

    public BolusType getBolusType() {
        return this.bolusType;
    }

    public double getInitialAmount() {
        return this.initialAmount;
    }

    public double getRemainingAmount() {
        return this.remainingAmount;
    }

    public int getRemainingDuration() {
        return this.remainingDuration;
    }

    public void setBolusID(int bolusID) {
        this.bolusID = bolusID;
    }

    public void setBolusType(BolusType bolusType) {
        this.bolusType = bolusType;
    }

    public void setInitialAmount(double initialAmount) {
        this.initialAmount = initialAmount;
    }

    public void setRemainingAmount(double remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public void setRemainingDuration(int remainingDuration) {
        this.remainingDuration = remainingDuration;
    }
}
