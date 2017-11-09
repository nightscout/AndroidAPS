package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.PumpEnactResult;

/**
 * Created by mike on 09.11.2017.
 */
public class Callback {
    public PumpEnactResult result;
    Runnable runnable;

    public Callback(Runnable runnable) {
        this.runnable = runnable;
    }

    public Callback result(PumpEnactResult result) {
        this.result = result;
        return this;
    }

    public void run() {
        runnable.run();
    }
}
