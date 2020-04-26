package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Basal_Get_Profile_Number extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Basal_Get_Profile_Number() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Requesting active profile");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        pump.activeProfile = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Active profile: " + pump.activeProfile);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__GET_PROFILE_NUMBER";
    }
}
