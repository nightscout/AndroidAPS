package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.Loop.APSResult;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstraintsInterface {

    boolean isLoopEnabled();

    boolean isClosedModeEnabled();

    boolean isAutosensModeEnabled();

    boolean isAMAModeEnabled();

    boolean isSMBModeEnabled();

    Double applyBasalConstraints(Double absoluteRate);

    Integer applyBasalConstraints(Integer percentRate);

    Double applyBolusConstraints(Double insulin);

    Integer applyCarbsConstraints(Integer carbs);

    Double applyMaxIOBConstraints(Double maxIob);

}
