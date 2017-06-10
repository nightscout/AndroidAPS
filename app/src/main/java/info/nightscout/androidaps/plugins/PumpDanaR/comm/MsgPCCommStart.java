package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgPCCommStart extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgPCCommStart.class);
    public MsgPCCommStart() {
        SetCommand(0x3001);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (Config.logDanaMessageDetail)
            log.debug("PC comm start received");
    }
}
