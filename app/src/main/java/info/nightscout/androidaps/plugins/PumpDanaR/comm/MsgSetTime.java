package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;

/**
 * Created by mike on 09.12.2016.
 */

public class MsgSetTime extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetTime.class);
    private static Date time;

    public MsgSetTime(Date time) {
        SetCommand(0x330a);
        this.time = time;
        AddParamDateTime(time);
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);

        if (Config.logDanaMessageDetail)
            log.debug("Result of setting time: " + time + " is " + result);
    }
}
