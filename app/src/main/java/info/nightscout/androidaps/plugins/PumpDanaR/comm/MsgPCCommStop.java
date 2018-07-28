package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;

public class MsgPCCommStop extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(Constants.PUMPCOMM);
    public MsgPCCommStop() {
        SetCommand(0x3002);
        if (Config.logPumpComm)
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (Config.logPumpComm)
            log.debug("PC comm stop received");
    }
}
