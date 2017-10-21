package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Refill extends DanaRS_Packet_History_ {

	public DanaRS_Packet_History_Refill() {
		super();
		opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__REFILL;
	}

	public DanaRS_Packet_History_Refill(Date from) {
		super(from);
		opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__REFILL;
	}

	@Override
	public String getFriendlyName() {
		return "REVIEW__REFILL";
	}
}
