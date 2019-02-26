package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_Initial_Bolus extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int bolusRate01;
    private int bolusRate02;
    private int bolusRate03;
    private int bolusRate04;

    public DanaRS_Packet_Bolus_Set_Initial_Bolus() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_RATE;
    }

    public DanaRS_Packet_Bolus_Set_Initial_Bolus(double bolusRate01, double bolusRate02, double bolusRate03, double bolusRate04) {
        this();
        this.bolusRate01 = (int) (bolusRate01 / 100d);
        this.bolusRate02 = (int) (bolusRate02 / 100d);
        this.bolusRate03 = (int) (bolusRate03 / 100d);
        this.bolusRate04 = (int) (bolusRate04 / 100d);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[8];
        request[0] = (byte) (bolusRate01 & 0xff);
        request[1] = (byte) ((bolusRate01 >>> 8) & 0xff);
        request[2] = (byte) (bolusRate02 & 0xff);
        request[3] = (byte) ((bolusRate02 >>> 8) & 0xff);
        request[4] = (byte) (bolusRate03 & 0xff);
        request[5] = (byte) ((bolusRate03 >>> 8) & 0xff);
        request[6] = (byte) (bolusRate04 & 0xff);
        request[7] = (byte) ((bolusRate04 >>> 8) & 0xff);
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
        return "BOLUS__SET_BOLUS_RATE";
    }
}
