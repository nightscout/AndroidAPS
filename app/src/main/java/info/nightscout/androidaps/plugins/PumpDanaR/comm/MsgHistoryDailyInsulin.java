package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryDailyInsulin extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryDailyInsulin.class);
    public MsgHistoryDailyInsulin() {
        SetCommand(0x3102);
    }
    // Handle message taken from MsgHistoryAll
}
