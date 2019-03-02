package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_CIR_CF_Array extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int cir01;
    private int cir02;
    private int cir03;
    private int cir04;
    private int cir05;
    private int cir06;
    private int cir07;
    private int cf01;
    private int cf02;
    private int cf03;
    private int cf04;
    private int cf05;
    private int cf06;
    private int cf07;

    public DanaRS_Packet_Bolus_Set_CIR_CF_Array() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_CIR_CF_ARRAY;
    }

    public DanaRS_Packet_Bolus_Set_CIR_CF_Array(int cir01, int cir02, int cir03, int cir04, int cir05, int cir06, int cir07, int cf01, int cf02, int cf03, int cf04, int cf05, int cf06, int cf07) {
        this();
        this.cir01 = cir01;
        this.cir02 = cir02;
        this.cir03 = cir03;
        this.cir04 = cir04;
        this.cir05 = cir05;
        this.cir06 = cir06;
        this.cir07 = cir07;
        this.cf01 = cf01;
        this.cf02 = cf02;
        this.cf03 = cf03;
        this.cf04 = cf04;
        this.cf05 = cf05;
        this.cf06 = cf06;
        this.cf07 = cf07;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[28];
        request[0] = (byte) (cir01 & 0xff);
        request[1] = (byte) ((cir01 >>> 8) & 0xff);
        request[2] = (byte) (cir02 & 0xff);
        request[3] = (byte) ((cir02 >>> 8) & 0xff);
        request[4] = (byte) (cir03 & 0xff);
        request[5] = (byte) ((cir03 >>> 8) & 0xff);
        request[6] = (byte) (cir04 & 0xff);
        request[7] = (byte) ((cir04 >>> 8) & 0xff);
        request[8] = (byte) (cir05 & 0xff);
        request[9] = (byte) ((cir05 >>> 8) & 0xff);
        request[10] = (byte) (cir06 & 0xff);
        request[11] = (byte) ((cir06 >>> 8) & 0xff);
        request[12] = (byte) (cir07 & 0xff);
        request[13] = (byte) ((cir07 >>> 8) & 0xff);
        request[14] = (byte) (cf01 & 0xff);
        request[15] = (byte) ((cf01 >>> 8) & 0xff);
        request[16] = (byte) (cf02 & 0xff);
        request[17] = (byte) ((cf02 >>> 8) & 0xff);
        request[18] = (byte) (cf03 & 0xff);
        request[19] = (byte) ((cf03 >>> 8) & 0xff);
        request[20] = (byte) (cf04 & 0xff);
        request[21] = (byte) ((cf04 >>> 8) & 0xff);
        request[22] = (byte) (cf05 & 0xff);
        request[23] = (byte) ((cf05 >>> 8) & 0xff);
        request[24] = (byte) (cf06 & 0xff);
        request[25] = (byte) ((cf06 >>> 8) & 0xff);
        request[26] = (byte) (cf07 & 0xff);
        request[27] = (byte) ((cf07 >>> 8) & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (result != 0)
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
        return "BOLUS__SET_CIR_CF_ARRAY";
    }

}
