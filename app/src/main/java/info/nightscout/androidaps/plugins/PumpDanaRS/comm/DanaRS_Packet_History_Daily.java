package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Daily extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_Daily() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__DAILY;
    }

    public DanaRS_Packet_History_Daily(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__DAILY;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__DAILY";
    }
}
