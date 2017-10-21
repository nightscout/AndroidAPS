package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Etc_Keep_Connection extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Etc_Keep_Connection.class);

    public DanaRS_Packet_Etc_Keep_Connection() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + error);
        }
    }

    @Override
    public String getFriendlyName() {
        return "ETC__KEEP_CONNECTION";
    }
}
