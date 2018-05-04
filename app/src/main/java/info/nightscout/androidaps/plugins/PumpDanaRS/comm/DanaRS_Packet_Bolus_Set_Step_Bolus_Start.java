package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;

public class DanaRS_Packet_Bolus_Set_Step_Bolus_Start extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Set_Step_Bolus_Start.class);

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

        // HARDCODED LIMIT
        amount = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();

        this.amount = amount;
        this.speed = speed;

        if (Config.logDanaMessageDetail)
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
        if (Config.logDanaMessageDetail) {
            if (errorCode == 0) {
                log.debug("Result OK");
                failed = false;
            } else {
                failed = true;
                log.error("Result Error: " + errorCode);
            }
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_STEP_BOLUS_START";
    }
}
