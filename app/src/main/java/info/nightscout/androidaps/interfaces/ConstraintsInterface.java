package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.data.Profile;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstraintsInterface {

    Constraint<Boolean> isLoopInvokationAllowed(Constraint<Boolean> value);

    Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value);

    Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value);

    Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value);

    Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value);

    Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile);

    Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile);

    Constraint<Double>  applyBolusConstraints(Constraint<Double>  insulin);

    Constraint<Integer> applyCarbsConstraints(Constraint<Integer> carbs);

    default Constraint<Double> applyMaxIOBConstraints(Constraint<Double> maxIob) {
        return maxIob;
    };

}
