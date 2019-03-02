package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

public class MsgStatusAPS_v2 extends MessageBase {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgStatusAPS_v2() {
        SetCommand(0xE001);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        double iob = intFromBuff(bytes, 0, 2) / 100d;
        double deliveredSoFar = intFromBuff(bytes, 2, 2) / 100d;

        DanaRPump pump = DanaRPump.getInstance();
        pump.iob = iob;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Delivered so far: " + deliveredSoFar);
            log.debug("Current pump IOB: " + iob);
        }
    }

}
