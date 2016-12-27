package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;

/**
 * Created by mike on 09.12.2016.
 */

public class MsgSetTime extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetTime.class);

    public MsgSetTime(Date time) {
        SetCommand(0x330a);
        AddParamDateTime(time);
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);

        if (Config.logDanaMessageDetail)
            log.debug("Result: " + result);
    }
}
