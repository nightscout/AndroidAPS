package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class MsgPCCommStop extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    public MsgPCCommStop() {
        SetCommand(0x3002);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("PC comm stop received");
    }
}
