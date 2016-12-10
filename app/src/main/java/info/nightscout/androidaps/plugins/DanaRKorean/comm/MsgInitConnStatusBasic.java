package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;

public class MsgInitConnStatusBasic extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBasic.class);

    public MsgInitConnStatusBasic() {
        SetCommand(0x0303);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 > 6) {
            return;
        }
        DanaRKoreanPump pump = DanaRKoreanPlugin.getDanaRPump();
        int isStatusSuspendOn = intFromBuff(bytes, 0, 1);
        int isUtilityEnable = intFromBuff(bytes, 1, 1);
        int isEasyUIEnable = intFromBuff(bytes, 2, 1);
        int easyUIMode = intFromBuff(bytes, 3, 1);
        pump.password = intFromBuff(bytes, 4, 2) ^ 0x3463;
        if (Config.logDanaMessageDetail) {
            log.debug("isStatusSuspendOn: " + isStatusSuspendOn);
            log.debug("isUtilityEnable: " + isUtilityEnable);
            log.debug("isEasyUIEnable: " + isEasyUIEnable);
            log.debug("easyUIMode: " + easyUIMode);
            log.debug("Pump password: " + pump.password);
        }
    }
}
