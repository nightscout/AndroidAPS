package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel.class);

    public DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL;

        if (Config.logDanaMessageDetail)
            log.debug("Cancel extended bolus");
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
        return "BOLUS__SET_EXTENDED_BOLUS_CANCEL";
    }
}
