package info.nightscout.androidaps.plugins.DanaR.comm;

import com.squareup.otto.Bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusProgress;

public class MsgBolusStop extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgBolusStop.class);
    private static Treatment t;
    private static Double amount;
    private static Bus bus = null;

    public static boolean stopped = false;
    public static boolean forced = false;

    public MsgBolusStop() {
        SetCommand(0x0101);
        stopped = false;
    }

    public MsgBolusStop(Bus bus, Double amount, Treatment t) {
        this();
        this.bus = bus;
        this.t = t;
        this.amount = amount;
        forced = false;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        EventDanaRBolusProgress bolusingEvent = EventDanaRBolusProgress.getInstance();
        stopped = true;
        if (!forced) {
            t.insulin = amount;
            bolusingEvent.sStatus = "Delivered";
        } else {
            bolusingEvent.sStatus = "Stopped";
        }
        bus.post(bolusingEvent);
    }
}
