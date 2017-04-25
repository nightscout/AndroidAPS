package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryBasalHour extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryBasalHour.class);
    public MsgHistoryBasalHour() {
        SetCommand(0x310A);
    }
    // Handle message taken from MsgHistoryAll
}
