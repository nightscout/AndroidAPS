package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Basal_Set_Suspend_On extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Basal_Set_Suspend_On.class);

    public DanaRS_Packet_Basal_Set_Suspend_On() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_ON;
        if (Config.logDanaMessageDetail) {
            log.debug("Turning on suspend");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (Config.logDanaMessageDetail) {
            if (result == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__SET_SUSPEND_ON";
    }
}
