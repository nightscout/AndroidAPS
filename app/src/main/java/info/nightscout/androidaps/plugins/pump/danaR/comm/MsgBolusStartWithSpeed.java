package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.logging.L;

public class MsgBolusStartWithSpeed extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public static int errorCode;

    public MsgBolusStartWithSpeed() {
        SetCommand(0x0104);
    }

    public MsgBolusStartWithSpeed(double amount, int speed) {
        this();

        // HARDCODED LIMIT
        amount = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();

        AddParamInt((int) (amount * 100));
        AddParamByte((byte) speed);

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Bolus start : " + amount + " speed: " + speed);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        errorCode = intFromBuff(bytes, 0, 1);
        if (errorCode != 2) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Messsage response: " + errorCode + " FAILED!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Messsage response: " + errorCode + " OK");
        }
    }
}
