package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear.class);

    public DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR;
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
        return "REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR";
    }
}
