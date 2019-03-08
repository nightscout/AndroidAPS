package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by mike on 09.12.2016.
 */

public class MsgSetTime extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private static Date time;

    public MsgSetTime(Date time) {
        SetCommand(0x330a);
        this.time = time;
        AddParamDateTime(time);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message: time:" + DateUtil.dateAndTimeString(time));
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
        }

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Result of setting time: " + time + " is " + result);
    }
}
