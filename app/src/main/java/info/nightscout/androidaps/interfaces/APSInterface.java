package info.nightscout.androidaps.interfaces;

import java.util.Date;

import info.nightscout.androidaps.plugins.Loop.APSResult;

/**
 * Created by mike on 10.06.2016.
 */
public interface APSInterface {
    public APSResult getLastAPSResult();
    public Date getLastAPSRun();

    public void invoke(String initiator, boolean tempBasalFallback);
}
