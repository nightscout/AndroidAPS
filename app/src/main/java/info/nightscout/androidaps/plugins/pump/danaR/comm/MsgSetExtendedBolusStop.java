package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

public class MsgSetExtendedBolusStop extends MessageBase {
    private static Logger log = StacktraceLoggerWrapper.getLogger(L.PUMPCOMM);

    public MsgSetExtendedBolusStop() {
        SetCommand(0x0406);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set extended bolus stop result: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set extended bolus stop result: " + result);
        }
    }


}
