package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.APSResult;

/**
 * Created by mike on 15.06.2016.
 */
public interface ConstrainsInterface {

    boolean isAutomaticProcessingEnabled();
    boolean manualConfirmationNeeded();
    APSResult applyBasalConstrains(APSResult result);
}
