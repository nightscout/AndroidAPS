package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Bolus_Set_Extended_Bolus extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Set_Extended_Bolus.class);

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

        if (Config.logDanaMessageDetail)
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
        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + status);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_EXTENDED_BOLUS";
    }
}
