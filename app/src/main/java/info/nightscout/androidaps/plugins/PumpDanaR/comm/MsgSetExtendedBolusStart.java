package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.utils.HardLimits;

public class MsgSetExtendedBolusStart extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetExtendedBolusStart.class);

    public MsgSetExtendedBolusStart() {
        SetCommand(0x0407);
    }

    public MsgSetExtendedBolusStart(double amount, byte halfhours) {
        this();

        // HARDCODED LIMITS
        if (halfhours < 1) halfhours = 1;
        if (halfhours > 16) halfhours = 16;
        amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
        if (amount < 0d) amount = 0d;
        if (amount > HardLimits.maxBolus()) amount = HardLimits.maxBolus();

        AddParamInt((int) (amount * 100));
        AddParamByte(halfhours);
        if (Config.logDanaMessageDetail)
            log.debug("Set extended bolus start: " + (((int) (amount * 100)) / 100d) + "U halfhours: " + (int) halfhours);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Set extended bolus start result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set extended bolus start result: " + result);
        }
    }
}
