package info.nightscout.androidaps.interfaces.constrains;

/**
 * Created by mike on 19.03.2018.
 */

public class BooleanConstraint extends Constraint {
    boolean value;

    public BooleanConstraint(boolean value) {
        this.value = value;
    }

    public boolean get() {
        return value;
    }

    public BooleanConstraint set(boolean value) {
        this.value = value;
        return this;
    }

    public BooleanConstraint set(boolean value, String reason) {
        this.value = value;
        reason(reason);
        return this;
    }

}
