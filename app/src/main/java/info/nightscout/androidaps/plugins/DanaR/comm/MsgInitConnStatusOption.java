package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusOption extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusOption.class);

    public MsgInitConnStatusOption() {
        SetCommand(0x0304);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int status1224Clock = intFromBuff(bytes, 0, 1);
        int isStatusButtonScroll = intFromBuff(bytes, 1, 1);
        int soundVibration = intFromBuff(bytes, 2, 1);
        int glucoseUnit = intFromBuff(bytes, 3, 1);
        int lcdTimeout = intFromBuff(bytes, 4, 1);
        int backlightgTimeout = intFromBuff(bytes, 5, 1);
        int languageOption = intFromBuff(bytes, 6, 1);
        int lowReservoirAlarmBoundary = intFromBuff(bytes, 7, 1);
        //int none = intFromBuff(bytes, 8, 1);
        if (bytes.length >= 21) {
            DanaRPlugin.getDanaRPump().password = intFromBuff(bytes, 9, 2) ^ 0x3463;
            if (Config.logDanaMessageDetail)
                log.debug("Pump password: " + DanaRPlugin.getDanaRPump().password);
        }
    }

}
