package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.APSResult;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstraintsInterface {

    boolean isClosedModeEnabled();
    APSResult applyBasalConstraints(APSResult request);
    Double applyBasalConstraints(Double absoluteRate);
    Integer applyBasalConstraints(Integer percentRate);

    Double applyBolusConstraints(Double insulin);
    Integer applyCarbsConstraints(Integer carbs);
}
