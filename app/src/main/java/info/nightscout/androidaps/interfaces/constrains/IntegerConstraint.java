package info.nightscout.androidaps.interfaces.constrains;

/**
 * Created by mike on 19.03.2018.
 */

public class IntegerConstraint extends Constraint {
    int value;

    public IntegerConstraint(int value) {
        this.value = value;
    }

    public int getInteger() {
        return value;
    }

    public IntegerConstraint set(int value) {
        this.value = value;
        return this;
    }

    public IntegerConstraint set(int value, String reason) {
        this.value = value;
        reason(reason);
        return this;
    }
}
