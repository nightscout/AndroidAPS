package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryNewDone extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryNewDone.class);
    public static boolean received = false;

    public MsgHistoryNewDone() {
        SetCommand(0x42F1);
        received = false;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        received = true;
        if (Config.logDanaMessageDetail)
            log.debug("History new done received");
    }
}
