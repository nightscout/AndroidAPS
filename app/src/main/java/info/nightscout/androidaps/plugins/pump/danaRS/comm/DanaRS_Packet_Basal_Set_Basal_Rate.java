package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Basal_Set_Basal_Rate extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private double[] profileBasalRate;

    public DanaRS_Packet_Basal_Set_Basal_Rate() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_BASAL_RATE;
    }

    public DanaRS_Packet_Basal_Set_Basal_Rate(double[] profileBasalRate) {
        this();
        this.profileBasalRate = profileBasalRate;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Setting new basal rates");
        }
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[48];
        for (int i = 0, size = 24; i < size; i++) {
            int rate = (int) (profileBasalRate[i] * 100d);
            request[0 + (i * 2)] = (byte) (rate & 0xff);
            request[1 + (i * 2)] = (byte) ((rate >>> 8) & 0xff);
        }
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if(result != 0)
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
        return "BASAL__SET_BASAL_RATE";
    }
}
