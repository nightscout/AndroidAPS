package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryGlucose extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryGlucose.class);
    public MsgHistoryGlucose() {
        SetCommand(0x3104);
    }
    // Handle message taken from MsgHistoryAll
}
