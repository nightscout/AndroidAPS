package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandBolus extends Command {
    DetailedBolusInfo detailedBolusInfo;

    public CommandBolus(DetailedBolusInfo detailedBolusInfo, Callback callback) {
        commandType = CommandType.BOLUS;
        this.detailedBolusInfo = detailedBolusInfo;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = MainApp.getConfigBuilder().deliverTreatment(detailedBolusInfo);
        if (callback != null)
            callback.result(r).run();
    }

    public String status() {
        return "BOLUS " + DecimalFormatter.to1Decimal(detailedBolusInfo.insulin) + "U";
    }
}
