package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_General_Get_Shipping_Information extends DanaRS_Packet {
	private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Set_Step_Bolus_Stop.class);

	public DanaRS_Packet_General_Get_Shipping_Information() {
		super();
		opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION;
	}

	@Override
	public void handleMessage(byte[] data) {
		DanaRPump pump = DanaRPump.getInstance();

		int dataIndex = DATA_START;
		int dataSize = 10;
		pump.serialNumber = stringFromBuff(data, dataIndex, dataSize);

		dataIndex += dataSize;
		dataSize = 3;
		pump.shippingDate = dateFromBuff(data, dataIndex);

		dataIndex += dataSize;
		dataSize = 3;
		pump.shippingCountry = asciiStringFromBuff(data, dataIndex, dataSize);

		if (Config.logDanaMessageDetail) {
			log.debug("Serial number: " + pump.serialNumber);
			log.debug("Shipping date: " + pump.shippingDate);
			log.debug("Shipping country: " + pump.shippingCountry);
		}
	}

	@Override
	public String getFriendlyName() {
		return "REVIEW__GET_SHIPPING_INFORMATION";
	}
}
