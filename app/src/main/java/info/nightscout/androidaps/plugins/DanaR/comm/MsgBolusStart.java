package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class MsgBolusStart extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgBolusStart.class);

    public MsgBolusStart() {
        SetCommand(0x0102);
    }

    public MsgBolusStart(double amount) {
        this();
        AddParamInt((int) (amount * 100));
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 2) {
            failed = true;
            log.debug("Messsage response: " + result + " FAILED!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Messsage response: " + result);
        }
    }
}
