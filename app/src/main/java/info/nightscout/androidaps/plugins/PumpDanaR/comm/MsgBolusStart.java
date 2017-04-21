package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.utils.HardLimits;

public class MsgBolusStart extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgBolusStart.class);

    public MsgBolusStart() {
        SetCommand(0x0102);
    }

    public MsgBolusStart(double amount) {
        this();

        // HARDCODED LIMIT
        amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
        if (amount < 0) amount = 0d;
        if (amount > HardLimits.maxBolus()) amount = HardLimits.maxBolus();

        AddParamInt((int) (amount * 100));

        if (Config.logDanaMessageDetail)
            log.debug("Bolus start : " + amount);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 2) {
            failed = true;
            log.debug("Messsage response: " + result + " FAILED!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Messsage response: " + result);
        }
    }
}
