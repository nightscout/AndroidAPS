package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;

public class MsgStatusAPSTempBasal extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusAPSTempBasal.class);

    public MsgStatusAPSTempBasal() {
        SetCommand(0xE001);
    }

    public void handleMessage(byte[] bytes) {
        double iob = intFromBuff(bytes, 0, 2) / 100d;
        int tempBasalPercent = intFromBuff(bytes, 2, 2);

        DanaRPump pump = DanaRPump.getInstance();
        pump.isTempBasalInProgress = tempBasalPercent != 100;
        pump.tempBasalPercent = tempBasalPercent;
        pump.iob = iob;


        if (Config.logDanaMessageDetail) {
            log.debug("Is APS temp basal running: " + pump.isTempBasalInProgress);
            log.debug("Current APS temp basal percent: " + tempBasalPercent);
            log.debug("Current pump IOB: " + iob);
        }
    }

}
