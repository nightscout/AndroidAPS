package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;

public class MsgInitConnStatusTime extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusTime.class);

    public MsgInitConnStatusTime() {
        SetCommand(0x0301);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        Date time = dateTimeSecFromBuff(bytes, 0);

        if (Config.logDanaMessageDetail)
            log.debug("Pump time: " + time);
    }
}
