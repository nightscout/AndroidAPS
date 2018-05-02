package info.nightscout.androidaps.plugins.PumpCommon.data;

/**
 * Created by andy on 02/05/2018.
 */

public class DoseSettings {

    private float step;
    private int durationStep;
    private int maxDuration;
    private float minDose;
    private Float maxDose;

    public DoseSettings(float step, int durationStep, int maxDuration, float minDose, Float maxDose)
    {
        this.step = step;
        this.durationStep = durationStep;
        this.maxDuration = maxDuration;
        this.minDose = minDose;
        this.maxDose = maxDose;
    }

    public DoseSettings(float step, int durationStep, int maxDuration, float minDose)
    {
        this(step, durationStep, maxDuration, minDose, null);
    }


    public float getStep() {
        return step;
    }

    public int getDurationStep() {
        return durationStep;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public float getMinDose() {
        return minDose;
    }

    public Float getMaxDose() {
        return maxDose;
    }
}
