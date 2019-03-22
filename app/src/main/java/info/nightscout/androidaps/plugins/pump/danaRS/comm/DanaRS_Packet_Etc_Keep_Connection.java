package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Etc_Keep_Connection extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Etc_Keep_Connection() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (error!=0)
            failed=true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Result: " + error);
        }
    }

    @Override
    public String getFriendlyName() {
        return "ETC__KEEP_CONNECTION";
    }
}
