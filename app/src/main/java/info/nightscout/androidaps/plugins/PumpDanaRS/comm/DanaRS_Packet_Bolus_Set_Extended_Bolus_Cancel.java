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
        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + status);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_EXTENDED_BOLUS_CANCEL";
    }
}
