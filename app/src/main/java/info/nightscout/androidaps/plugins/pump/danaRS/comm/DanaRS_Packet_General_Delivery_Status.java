package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_General_Delivery_Status extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Delivery_Status() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__DELIVERY_STATUS;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (status != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Status: " + status);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__DELIVERY_STATUS";
    }
}
