package info.nightscout.androidaps.interfaces.constrains;

/**
 * Created by mike on 19.03.2018.
 */

public class DoubleConstraint extends Constraint{
    double value;

    public DoubleConstraint(double value) {
        this.value = value;
    }

    public Double getDouble() {
        return value;
    }

    public DoubleConstraint set(double value) {
        this.value = value;
        return this;
    }

    public DoubleConstraint set(double value, String reason) {
        this.value = value;
        reason(reason);
        return this;
    }

}
