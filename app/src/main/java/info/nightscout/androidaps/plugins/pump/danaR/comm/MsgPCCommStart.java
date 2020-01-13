package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

public class MsgPCCommStart extends MessageBase {
    private static Logger log = StacktraceLoggerWrapper.getLogger(L.PUMPCOMM);

    public MsgPCCommStart() {
        SetCommand(0x3001);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("PC comm start received");
    }
}
