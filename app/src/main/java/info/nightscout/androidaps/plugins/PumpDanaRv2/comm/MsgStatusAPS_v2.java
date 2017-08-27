package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;

public class MsgStatusAPS_v2 extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusAPS_v2.class);

    public MsgStatusAPS_v2() {
        SetCommand(0xE001);
    }

    public void handleMessage(byte[] bytes) {
        double iob = intFromBuff(bytes, 0, 2) / 100d;
        double deliveredSoFar = intFromBuff(bytes, 2, 2) / 100d;

        DanaRPump pump = DanaRPump.getInstance();
        pump.iob = iob;

        if (Config.logDanaMessageDetail) {
            log.debug("Delivered so far: " + deliveredSoFar);
            log.debug("Current pump IOB: " + iob);
        }
    }

}
