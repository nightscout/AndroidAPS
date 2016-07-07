package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusOption extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusOption.class);

    public MsgInitConnStatusOption() {
        SetCommand(0x0304);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int a = intFromBuff(bytes, 0, 1);
        int b = intFromBuff(bytes, 1, 1);
        int c = intFromBuff(bytes, 2, 1);
        int d = intFromBuff(bytes, 3, 1);
        int e = intFromBuff(bytes, 4, 1);
        int f = intFromBuff(bytes, 5, 1);
        int g = intFromBuff(bytes, 6, 1);
        int h = intFromBuff(bytes, 7, 1);
        int i = intFromBuff(bytes, 8, 1);
        if (bytes.length >= 21) {
            DanaRFragment.getDanaRPump().accessCode = intFromBuff(bytes, 9, 2) ^ 0x3463;
            DanaRFragment.getDanaRPump().isNewPump = true;
            if (Config.logDanaMessageDetail)
                log.debug("Pump password: " + DanaRFragment.getDanaRPump().accessCode);
        }
    }

}
