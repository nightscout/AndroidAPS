package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusBolus extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBolus.class);

    public MsgInitConnStatusBolus() {
        SetCommand(0x0302);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int a1 = intFromBuff(bytes, 0, 1);
        int a2 = intFromBuff(bytes, 1, 1);
        int c = intFromBuff(bytes, 8, 2);
        int d = c / 100;
        int e = intFromBuff(bytes, 10, 2);
    }
}
