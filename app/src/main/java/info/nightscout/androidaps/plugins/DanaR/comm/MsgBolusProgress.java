package info.nightscout.androidaps.plugins.DanaR.comm;

import com.squareup.otto.Bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusProgress;

public class MsgBolusProgress extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgBolusProgress.class);
    public static final DecimalFormat bolusNumberFormat = new DecimalFormat("0.0");
    private static Bus bus = null;

    private static Treatment t;
    private static double amount;

    public int progress = -1;

    public MsgBolusProgress() {
        SetCommand(0x0202);
    }

    public MsgBolusProgress(Bus bus, double amount, Treatment t) {
        this();
        this.amount = amount;
        this.t = t;
        this.bus = bus;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        progress = intFromBuff(bytes, 0, 2);
        Double done = (amount * 100 - progress) / 100d;
        t.insulin = done;
        EventDanaRBolusProgress bolusingEvent = EventDanaRBolusProgress.getInstance();
        bolusingEvent.sStatus = "Delivering " + bolusNumberFormat.format(done) + "U";
        bolusingEvent.t = t;

        if (Config.logDanaMessageDetail) {
            log.debug("Bolus remaining: " + progress + " delivered: " + done);
        }

        bus.post(bolusingEvent);
    }
}
