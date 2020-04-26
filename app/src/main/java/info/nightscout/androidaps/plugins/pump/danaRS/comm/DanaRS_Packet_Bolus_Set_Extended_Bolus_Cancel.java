package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL;

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Cancel extended bolus");
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
        return "BOLUS__SET_EXTENDED_BOLUS_CANCEL";
    }
}
