package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */
public abstract class Command {
    public enum CommandType {
        BOLUS,
        SMB_BOLUS,
        CARBS_ONLY_TREATMENT,
        TEMPBASAL,
        EXTENDEDBOLUS,
        BASALPROFILE,
        READSTATUS,
        LOADHISTORY, // TDDs and so far only Dana specific
        LOADEVENTS // so far only Dana specific
    }

    public CommandType commandType;
    protected Callback callback;

    public abstract void execute();

    public abstract String status();

    public void cancel() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.gs(R.string.connectiontimedout);
        if (callback != null)
            callback.result(result).run();
    }
}
