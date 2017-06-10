package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistorySuspend extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistorySuspend.class);
    public MsgHistorySuspend() {
        SetCommand(0x3109);
    }
    // Handle message taken from MsgHistoryAll
}
