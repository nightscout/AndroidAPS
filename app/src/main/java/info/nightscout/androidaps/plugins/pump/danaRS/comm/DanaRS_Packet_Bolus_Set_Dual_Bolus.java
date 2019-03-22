package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_Dual_Bolus extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private double amount;
    private double extendedAmount;
    private int extendedBolusDurationInHalfHours;

    public DanaRS_Packet_Bolus_Set_Dual_Bolus() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_DUAL_BOLUS;
    }

    public DanaRS_Packet_Bolus_Set_Dual_Bolus(double amount, double extendedAmount, int extendedBolusDurationInHalfHours) {
        this();
        this.amount = amount;
        this.extendedAmount = extendedAmount;
        this.extendedBolusDurationInHalfHours = extendedBolusDurationInHalfHours;

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Dual bolus start : " + amount + " U extended: " + extendedAmount + " U halfhours: " + extendedBolusDurationInHalfHours);
    }

    @Override
    public byte[] getRequestParams() {
        int stepBolusRate = (int) (amount / 100d);
        int extendedBolusRate = (int) (extendedAmount / 100d);

        byte[] request = new byte[5];
        request[0] = (byte) (stepBolusRate & 0xff);
        request[1] = (byte) ((stepBolusRate >>> 8) & 0xff);
        request[2] = (byte) (extendedBolusRate & 0xff);
        request[3] = (byte) ((extendedBolusRate >>> 8) & 0xff);
        request[4] = (byte) (extendedBolusDurationInHalfHours & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (result!=0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_DUAL_BOLUS";
    }
}
