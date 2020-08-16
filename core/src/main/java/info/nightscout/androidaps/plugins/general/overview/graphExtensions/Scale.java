package info.nightscout.androidaps.plugins.general.overview.graphExtensions;

/**
 * Created by mike on 18.10.2017.
 */

public class Scale {
    private double multiplier;
    private double shift;

    public Scale() {
        shift = 0;
    }

    public Scale(double shift) {
        this.shift = shift;
    }

    public void setMultiplier(double value) {
        this.multiplier = value;
    }

    public double transform(double original) {
        return original * multiplier + shift;
    }

    public double getShift() {
        return shift;
    }
}
