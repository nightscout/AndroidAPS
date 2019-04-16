package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DecimalFormatter;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandBolus extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private DetailedBolusInfo detailedBolusInfo;

    public CommandBolus(DetailedBolusInfo detailedBolusInfo, Callback callback, CommandType type) {
        commandType = type;
        this.detailedBolusInfo = detailedBolusInfo;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getPlugin().getActivePump().deliverTreatment(detailedBolusInfo);

        BolusProgressDialog.bolusEnded = true;
        MainApp.bus().post(new EventDismissBolusprogressIfRunning(r));
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result success: " + r.success + " enacted: " + r.enacted);

        if (callback != null)
            callback.result(r).run();
    }

    public String status() {
        return (detailedBolusInfo.insulin > 0 ? "BOLUS " + DecimalFormatter.to1Decimal(detailedBolusInfo.insulin) + "U " : "") +
                (detailedBolusInfo.carbs > 0 ? "CARBS " + DecimalFormatter.to0Decimal(detailedBolusInfo.carbs) + "g" : "" );
    }
}
