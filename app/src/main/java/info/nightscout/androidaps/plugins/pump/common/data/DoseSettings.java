package info.nightscout.androidaps.plugins.pump.common.data;

/**
 * Created by andy on 02/05/2018.
 */

public class DoseSettings {

    private double step;
    private int durationStep;
    private int maxDuration;
    private double minDose;
    private Double maxDose;

    public DoseSettings(double step, int durationStep, int maxDuration, double minDose, Double maxDose)
    {
        this.step = step;
        this.durationStep = durationStep;
        this.maxDuration = maxDuration;
        this.minDose = minDose;
        this.maxDose = maxDose;
    }

    public DoseSettings(double step, int durationStep, int maxDuration, double minDose)
    {
        this(step, durationStep, maxDuration, minDose, Double.MAX_VALUE);
    }


    public double getStep() {
        return step;
    }

    public int getDurationStep() {
        return durationStep;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public double getMinDose() {
        return minDose;
    }

    public Double getMaxDose() {
        return maxDose;
    }
}
