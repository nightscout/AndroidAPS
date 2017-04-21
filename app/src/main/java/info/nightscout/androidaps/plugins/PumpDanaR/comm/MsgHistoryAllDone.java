package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgHistoryAllDone extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryAllDone.class);
    public static boolean received = false;

    public MsgHistoryAllDone() {
        SetCommand(0x41F1);
        received = false;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        received = true;
        if (Config.logDanaMessageDetail)
            log.debug("History all done received");
    }
}
