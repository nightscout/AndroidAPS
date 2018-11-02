package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.data.Profile;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstraintsInterface {

    default Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Boolean> isUAMEnabled(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Boolean> isAdvancedFilteringEnabled(Constraint<Boolean> value) {
        return value;
    }

    default Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {
        return absoluteRate;
    }

    default Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {
        return percentRate;
    }

    default Constraint<Double>  applyBolusConstraints(Constraint<Double>  insulin) {
        return insulin;
    }

    default Constraint<Double>  applyExtendedBolusConstraints(Constraint<Double>  insulin) {
        return insulin;
    }

    default Constraint<Integer> applyCarbsConstraints(Constraint<Integer> carbs) {
        return carbs;
    }

    default Constraint<Double> applyMaxIOBConstraints(Constraint<Double> maxIob) {
        return maxIob;
    };

}
