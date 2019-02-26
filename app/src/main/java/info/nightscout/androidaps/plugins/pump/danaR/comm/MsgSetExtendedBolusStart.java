package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.logging.L;

public class MsgSetExtendedBolusStart extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSetExtendedBolusStart() {
        SetCommand(0x0407);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public MsgSetExtendedBolusStart(double amount, byte halfhours) {
        this();

        // HARDCODED LIMITS
        if (halfhours < 1) halfhours = 1;
        if (halfhours > 16) halfhours = 16;
        Constraint<Double> constrainedAmount = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount));
        if (constrainedAmount != null) {
            AddParamInt((int) (constrainedAmount.value() * 100));
        } else {
            log.error("constrainedAmount of insulin is null!!");
            AddParamInt(0);
        }
        AddParamByte(halfhours);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Set extended bolus start: " + (((int) (amount * 100)) / 100d) + "U halfhours: " + (int) halfhours);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set extended bolus start result: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set extended bolus start result: " + result);
        }
    }
}
