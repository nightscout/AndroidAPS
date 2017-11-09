package info.nightscout.androidaps.queue;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandReadStatus extends Command {

    CommandReadStatus(Callback callback) {
        commandType = CommandType.READSTATUS;
        this.callback = callback;
    }

    @Override
    public void execute() {
        // do nothing by default. Status is read on connection
        if (callback != null)
            callback.result(null).run();
    }

    @Override
    public String status() {
        return "READ STATUS";
    }
}
