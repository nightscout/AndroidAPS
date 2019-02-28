package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.treatments.Treatment;

public class MsgBolusStop extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private static Treatment t;
    private static Double amount;

    public static boolean stopped = false;
    public static boolean forced = false;

    public MsgBolusStop() {
        SetCommand(0x0101);
        stopped = false;
    }

    public MsgBolusStop(Double amount, Treatment t) {
        this();
        this.t = t;
        this.amount = amount;
        forced = false;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Bolus stop: amount: " + amount + " treatment: " + t.toString());
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Messsage received");
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        stopped = true;
        if (!forced) {
            t.insulin = amount;
            bolusingEvent.status = MainApp.gs(R.string.overview_bolusprogress_delivered);
            bolusingEvent.percent = 100;
        } else {
            bolusingEvent.status = MainApp.gs(R.string.overview_bolusprogress_stoped);
        }
        MainApp.bus().post(bolusingEvent);
    }
}
