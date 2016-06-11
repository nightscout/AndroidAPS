package info.nightscout.androidaps.plugins;

import java.util.Date;

/**
 * Created by mike on 10.06.2016.
 */
public interface APSBase {
    public APSResult getLastAPSResult();
    public Date getLastAPSRun();

    public void invoke();
}
