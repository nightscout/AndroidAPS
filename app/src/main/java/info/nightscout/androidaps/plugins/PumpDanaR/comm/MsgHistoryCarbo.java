package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryCarbo extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(Constants.PUMPCOMM);
    public MsgHistoryCarbo() {
        SetCommand(0x3107);
        if (Config.logPumpComm)
            log.debug("New message");
    }
    // Handle message taken from MsgHistoryAll
}
