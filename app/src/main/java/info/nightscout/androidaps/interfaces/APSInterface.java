package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.aps.loop.APSResult;

/**
 * Created by mike on 10.06.2016.
 */
public interface APSInterface {
    APSResult getLastAPSResult();

    long getLastAPSRun();

    void invoke(String initiator, boolean tempBasalFallback);
}
