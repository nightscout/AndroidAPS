package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Basal_Set_Profile_Number extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private int profileNumber;

    public DanaRS_Packet_Basal_Set_Profile_Number() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER;
    }

    public DanaRS_Packet_Basal_Set_Profile_Number(int profileNumber) {
        this();
        this.profileNumber = profileNumber;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Setting profile number " + profileNumber);
        }
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[1];
        request[0] = (byte) (profileNumber & 0xff);
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
        return "BASAL__SET_PROFILE_NUMBER";
    }
}
