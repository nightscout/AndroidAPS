package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Blood_Glucose extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_Blood_Glucose() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE;
    }

    public DanaRS_Packet_History_Blood_Glucose(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__BLOOD_GLUCOSE";
    }
}
