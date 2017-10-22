package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import com.cozmo.danar.util.BleCommandUtil;
import info.nightscout.utils.HardLimits;

public class DanaRS_Packet_Bolus_Set_Step_Bolus_Start extends DanaRS_Packet {
	private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Set_Step_Bolus_Start.class);

	private double amount;
	private int speed;

	public boolean failed;

	public DanaRS_Packet_Bolus_Set_Step_Bolus_Start() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START;
    }

    // Speed 0 => 12 sec/U, 1 => 30 sec/U, 2 => 60 sec/U
	public DanaRS_Packet_Bolus_Set_Step_Bolus_Start(double amount, int speed) {
		this();

		// HARDCODED LIMIT
		amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
		if (amount < 0) amount = 0d;
		if (amount > HardLimits.maxBolus()) amount = HardLimits.maxBolus();

		this.amount = amount;
		this.speed = speed;

		if (Config.logDanaMessageDetail)
			log.debug("Bolus start : " + amount + " speed: " + speed);
	}

	@Override
	public byte[] getRequestParams() {
		int stepBolusRate = (int) (amount * 100);
		byte[] request = new byte[3];
		request[0] = (byte) (stepBolusRate & 0xff);
		request[1] = (byte) ((stepBolusRate >>> 8) & 0xff);
		request[2] = (byte) (speed & 0xff);
		return request;
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
		return "BOLUS__SET_STEP_BOLUS_START";
	}
}
