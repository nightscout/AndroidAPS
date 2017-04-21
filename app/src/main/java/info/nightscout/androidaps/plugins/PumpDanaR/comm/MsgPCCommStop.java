package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgPCCommStop extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgPCCommStop.class);
    public MsgPCCommStop() {
        SetCommand(0x3002);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (Config.logDanaMessageDetail)
            log.debug("PC comm stop received");
    }
}
