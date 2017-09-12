package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Prime extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_Prime() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__PRIME;
    }

    public DanaRS_Packet_History_Prime(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__PRIME;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__PRIME";
    }
}
