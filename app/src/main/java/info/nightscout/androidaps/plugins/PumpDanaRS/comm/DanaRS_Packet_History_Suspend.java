package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Suspend extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_Suspend() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SUSPEND;
    }

    public DanaRS_Packet_History_Suspend(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SUSPEND;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__SUSPEND";
    }
}
