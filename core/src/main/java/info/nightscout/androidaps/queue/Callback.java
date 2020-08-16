package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.PumpEnactResult;

/**
 * Created by mike on 09.11.2017.
 */
public abstract class Callback implements Runnable {
    public PumpEnactResult result;

    public Callback result(PumpEnactResult result) {
        this.result = result;
        return this;
    }
}
