package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_General_Delivery_Status extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Delivery_Status.class);

    public DanaRS_Packet_General_Delivery_Status() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__DELIVERY_STATUS;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (Config.logDanaMessageDetail) {
            log.debug("Status: " + status);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__DELIVERY_STATUS";
    }
}
