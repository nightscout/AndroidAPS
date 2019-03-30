package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Canceling temp basal");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else {
                log.error("Result Error: " + result);
                failed = true;
            }
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__CANCEL_TEMPORARY_BASAL";
    }

}
