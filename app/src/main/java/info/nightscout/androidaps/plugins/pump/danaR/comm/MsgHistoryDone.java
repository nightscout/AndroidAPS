package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryDone extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    public static boolean received = false;

    public MsgHistoryDone() {
        SetCommand(0x31F1);
        received = false;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        received = true;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("History done received");

    }
}
