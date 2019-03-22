package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class MsgHistoryAllDone extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    public static boolean received = false;

    public MsgHistoryAllDone() {
        SetCommand(0x41F1);
        received = false;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        received = true;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("History all done received");
    }
}
