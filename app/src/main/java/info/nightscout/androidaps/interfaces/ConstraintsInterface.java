package info.nightscout.androidaps.interfaces;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstraintsInterface {

    Constraint<Boolean> isLoopInvokationAllowed(Constraint<Boolean> value);

    Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value);

    Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value);

    Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value);

    boolean isSMBModeEnabled();

    Double applyBasalConstraints(Double absoluteRate);

    Integer applyBasalConstraints(Integer percentRate);

    Double applyBolusConstraints(Double insulin);

    Integer applyCarbsConstraints(Integer carbs);

    Double applyMaxIOBConstraints(Double maxIob);

}
