package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgInitConnStatusBasic extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBasic.class);

    public MsgInitConnStatusBasic() {
        SetCommand(0x0303);
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
        int j = intFromBuff(bytes, 9, 1);
        int k = intFromBuff(bytes, 10, 1);
        int l = intFromBuff(bytes, 11, 1);
        int m = intFromBuff(bytes, 12, 1);
        int n = intFromBuff(bytes, 13, 1);
        int o;
        try {
            o = intFromBuff(bytes, 21, 1);
        } catch (Exception ex) {
            o = 0;
        }
    }
}
