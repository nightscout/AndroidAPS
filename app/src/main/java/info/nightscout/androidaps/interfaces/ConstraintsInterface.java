package info.nightscout.androidaps.interfaces;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstraintsInterface {

    Constraint<Boolean> limitRunningLoop(Constraint<Boolean> value);

    Constraint<Boolean> limitClosedLoop(Constraint<Boolean> value);

    boolean isAutosensModeEnabled();

    boolean isAMAModeEnabled();

    boolean isSMBModeEnabled();

    Double applyBasalConstraints(Double absoluteRate);

    Integer applyBasalConstraints(Integer percentRate);

    Double applyBolusConstraints(Double insulin);

    Integer applyCarbsConstraints(Integer carbs);

    Double applyMaxIOBConstraints(Double maxIob);

}
