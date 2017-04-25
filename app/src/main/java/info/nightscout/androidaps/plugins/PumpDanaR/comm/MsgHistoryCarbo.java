package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryCarbo extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryCarbo.class);
    public MsgHistoryCarbo() {
        SetCommand(0x3107);
    }
    // Handle message taken from MsgHistoryAll
}
