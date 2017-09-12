package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Alarm extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_Alarm() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__ALARM;
    }

    public DanaRS_Packet_History_Alarm(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__ALARM;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__ALARM";
    }
}
