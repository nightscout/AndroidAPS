package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_Extended_Bolus extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private double extendedAmount;
    private int extendedBolusDurationInHalfHours;

    public DanaRS_Packet_Bolus_Set_Extended_Bolus() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS;
    }

    public DanaRS_Packet_Bolus_Set_Extended_Bolus(double extendedAmount, int extendedBolusDurationInHalfHours) {
        this();
        this.extendedAmount = extendedAmount;
        this.extendedBolusDurationInHalfHours = extendedBolusDurationInHalfHours;

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Extended bolus start : " + extendedAmount + " U halfhours: " + extendedBolusDurationInHalfHours);
    }

    @Override
    public byte[] getRequestParams() {
        int extendedBolusRate = (int) (extendedAmount * 100d);

        byte[] request = new byte[3];
        request[0] = (byte) (extendedBolusRate & 0xff);
        request[1] = (byte) ((extendedBolusRate >>> 8) & 0xff);
        request[2] = (byte) (extendedBolusDurationInHalfHours & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (result!=0)
            failed=true;
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_EXTENDED_BOLUS";
    }
}
