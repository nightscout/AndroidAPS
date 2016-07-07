package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

/**
 * Created by mike on 30.06.2016.
 */
public class MsgCheckValue extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgCheckValue.class);

    public MsgCheckValue() {
        SetCommand(0xF0F1);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int a = intFromBuff(bytes, 0, 1);
        int b = intFromBuff(bytes, 1, 1);
        if (a != 3 || b <= 0) {
            // another message will follow
        } else {

        }
        if (Config.logDanaMessageDetail)
            log.debug("Response: " + String.format("%02X ", a) + String.format("%02X ", b));
    }

}
