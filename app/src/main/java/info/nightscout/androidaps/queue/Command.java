package info.nightscout.androidaps.queue;

/**
 * Created by mike on 09.11.2017.
 */
public abstract class Command {
    enum CommandType {
        BOLUS,
        TEMPBASAL,
        EXTENDEDBOLUS,
        BASALPROFILE,
        READSTATUS
    }

    CommandType commandType;
    Callback callback;

    public abstract void execute();

    public abstract String status();
}
