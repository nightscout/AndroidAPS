package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class TotalDailyDose {

    private double bolus;
    private double basal;
    private double bolusAndBasal;

    public double getBolus() {
        return this.bolus;
    }

    public double getBasal() {
        return this.basal;
    }

    public double getBolusAndBasal() {
        return this.bolusAndBasal;
    }

    public void setBolus(double bolus) {
        this.bolus = bolus;
    }

    public void setBasal(double basal) {
        this.basal = basal;
    }

    public void setBolusAndBasal(double bolusAndBasal) {
        this.bolusAndBasal = bolusAndBasal;
    }
}
