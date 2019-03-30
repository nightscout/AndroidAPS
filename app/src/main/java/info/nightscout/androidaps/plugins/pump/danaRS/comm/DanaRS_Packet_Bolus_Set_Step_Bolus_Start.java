package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_Step_Bolus_Start extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private double amount;
    private int speed;

    public boolean failed;
    public static int errorCode;

    public DanaRS_Packet_Bolus_Set_Step_Bolus_Start() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START;
    }

    // Speed 0 => 12 sec/U, 1 => 30 sec/U, 2 => 60 sec/U
    public DanaRS_Packet_Bolus_Set_Step_Bolus_Start(double amount, int speed) {
        this();

        // HARDCODED LIMIT - if there is one that could be created
        amount = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();

        this.amount = amount;
        this.speed = speed;

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Bolus start : " + amount + " speed: " + speed);
    }

    @Override
    public byte[] getRequestParams() {
        int stepBolusRate = (int) (amount * 100);
        byte[] request = new byte[3];
        request[0] = (byte) (stepBolusRate & 0xff);
        request[1] = (byte) ((stepBolusRate >>> 8) & 0xff);
        request[2] = (byte) (speed & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        errorCode = intFromBuff(data, 0, 1);
        if (errorCode != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            if (errorCode == 0) {
                log.debug("Result OK");
            } else {
                log.error("Result Error: " + errorCode);
            }
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_STEP_BOLUS_START";
    }
}
