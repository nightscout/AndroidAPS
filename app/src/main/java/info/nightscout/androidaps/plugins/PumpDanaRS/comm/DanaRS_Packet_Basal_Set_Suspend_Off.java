package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Basal_Set_Suspend_Off extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Basal_Set_Suspend_Off.class);
    public int error;

    public DanaRS_Packet_Basal_Set_Suspend_Off() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF;
        if (Config.logDanaMessageDetail) {
            log.debug("Turning off suspend");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + error);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__SET_SUSPEND_OFF";
    }
}
