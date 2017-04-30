package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryDone extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryDone.class);
    public static boolean received = false;

    public MsgHistoryDone() {
        SetCommand(0x31F1);
        received = false;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        received = true;
        if (Config.logDanaMessageDetail)
            log.debug("History done received");

    }
}
