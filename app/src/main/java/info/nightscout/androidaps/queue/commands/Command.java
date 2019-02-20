package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */
public abstract class Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    public enum CommandType {
        BOLUS,
        SMB_BOLUS,
        CARBS_ONLY_TREATMENT,
        TEMPBASAL,
        EXTENDEDBOLUS,
        BASALPROFILE,
        READSTATUS,
        LOADHISTORY, // TDDs and so far only Dana specific
        LOADEVENTS, // so far only Dana specific
        SETUSERSETTINGS, // so far only Dana specific,
        START_PUMP,
        STOP_PUMP,
        INSIGHT_SET_TBR_OVER_ALARM
    }

    public CommandType commandType;
    protected Callback callback;

    public abstract void execute();

    public abstract String status();

    public void cancel() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.gs(R.string.connectiontimedout);
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result cancel");
        if (callback != null)
            callback.result(result).run();
    }
}
