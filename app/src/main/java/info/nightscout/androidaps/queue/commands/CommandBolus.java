package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandBolus extends Command {
    DetailedBolusInfo detailedBolusInfo;

    public CommandBolus(DetailedBolusInfo detailedBolusInfo, Callback callback, CommandType type) {
        commandType = type;
        this.detailedBolusInfo = detailedBolusInfo;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().deliverTreatment(detailedBolusInfo);

        BolusProgressDialog.bolusEnded = true;
        MainApp.bus().post(new EventDismissBolusprogressIfRunning(r));

        if (callback != null)
            callback.result(r).run();
    }

    public String status() {
        return (detailedBolusInfo.insulin > 0 ? "BOLUS " + DecimalFormatter.to1Decimal(detailedBolusInfo.insulin) + "U " : "") +
                (detailedBolusInfo.carbs > 0 ? "CARBS " + DecimalFormatter.to0Decimal(detailedBolusInfo.carbs) + "g" : "" );
    }
}
