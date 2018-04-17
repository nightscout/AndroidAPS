package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_General_Get_User_Time_Change_Flag extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Get_User_Time_Change_Flag.class);

    public DanaRS_Packet_General_Get_User_Time_Change_Flag() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int userTimeChangeFlag = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("UserTimeChangeFlag: " + userTimeChangeFlag);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_USER_TIME_CHANGE_FLAG";
    }
}
