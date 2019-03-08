package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.aps.loop.APSResult;

/**
 * Created by mike on 10.06.2016.
 */
public interface APSInterface {
    public APSResult getLastAPSResult();
    public long getLastAPSRun();

    public void invoke(String initiator, boolean tempBasalFallback);
}
