package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_General_Get_User_Time_Change_Flag extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Get_User_Time_Change_Flag() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        if (data.length < 3){
            failed = true;
            return;
        }
        int dataIndex = DATA_START;
        int dataSize = 1;
        int userTimeChangeFlag = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("UserTimeChangeFlag: " + userTimeChangeFlag);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_USER_TIME_CHANGE_FLAG";
    }
}
