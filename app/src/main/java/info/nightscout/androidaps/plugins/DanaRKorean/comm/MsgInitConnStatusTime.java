package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;

public class MsgInitConnStatusTime extends MessageBase {
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
