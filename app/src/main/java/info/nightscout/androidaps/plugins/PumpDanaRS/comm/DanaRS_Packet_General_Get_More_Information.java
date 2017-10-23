package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_General_Get_More_Information extends DanaRS_Packet {
	private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Get_More_Information.class);

	public DanaRS_Packet_General_Get_More_Information() {
		super();
		opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION;
	}

	@Override
	public void handleMessage(byte[] data) {
		DanaRPump pump = DanaRPump.getInstance();

		int dataIndex = DATA_START;
		int dataSize = 2;
		pump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize));

		dataIndex += dataSize;
		dataSize = 2;
		pump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

		dataIndex += dataSize;
		dataSize = 1;
		pump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01;

		dataIndex += dataSize;
		dataSize = 2;
		pump.extendedBolusRemainingMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize));

		dataIndex += dataSize;
		dataSize = 2;
		double remainRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        Date lastBolusTime = new Date(); // it doesn't provide day only hour+min, workaround: expecting today
        dataIndex += dataSize;
        dataSize = 1;
        lastBolusTime.setHours(byteArrayToInt(getBytes(data, dataIndex, dataSize)));

        dataIndex += dataSize;
        dataSize = 1;
        lastBolusTime.setMinutes(byteArrayToInt(getBytes(data, dataIndex, dataSize)));

		dataIndex += dataSize;
		dataSize = 2;
		pump.lastBolusAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("Daily total units: " + pump.dailyTotalUnits + " U");
            log.debug("Is extended in progress: " + pump.isExtendedInProgress);
            log.debug("Extended bolus remaining minutes: " + pump.extendedBolusRemainingMinutes);
            log.debug("Last bolus time: " + lastBolusTime.toLocaleString());
            log.debug("Last bolus amount: " + pump.lastBolusAmount);
        }
	}

	@Override
	public String getFriendlyName() {
		return "REVIEW__GET_MORE_INFORMATION";
	}
}
