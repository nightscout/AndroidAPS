package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_All_History extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_All_History() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY;
    }

    public DanaRS_Packet_History_All_History(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__ALL_HISTORY";
    }
}
