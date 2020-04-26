package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryBolus extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgHistoryBolus() {
        SetCommand(0x3101);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }
    // Handle message taken from MsgHistoryAll
}
