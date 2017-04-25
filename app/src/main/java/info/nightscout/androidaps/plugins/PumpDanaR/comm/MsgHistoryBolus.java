package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryBolus extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryBolus.class);
    public MsgHistoryBolus() {
        SetCommand(0x3101);
    }
    // Handle message taken from MsgHistoryAll
}
