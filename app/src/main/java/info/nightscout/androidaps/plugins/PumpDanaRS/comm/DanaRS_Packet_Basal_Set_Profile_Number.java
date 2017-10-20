package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Basal_Set_Profile_Number extends DanaRS_Packet {
	private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Basal_Set_Profile_Number.class);
	private int profileNumber;
	public int error;

	public DanaRS_Packet_Basal_Set_Profile_Number() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER;
    }
	public DanaRS_Packet_Basal_Set_Profile_Number(int profileNumber) {
		this();
		this.profileNumber = profileNumber;
		if (Config.logDanaMessageDetail) {
			log.debug("Setting profile number " + profileNumber);
		}
	}

	@Override
	public byte[] getRequestParams() {
		byte[] request = new byte[1];
		request[0] = (byte) (profileNumber & 0xff);
		return request;
	}

    @Override
	public void handleMessage(byte[] data) {
		int dataIndex = DATA_START;
		int dataSize = 1;
		error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
		if (Config.logDanaMessageDetail) {
			log.debug("Result: " + error);
		}
	}

	@Override
	public String getFriendlyName() {
		return "BASAL__SET_PROFILE_NUMBER";
	}
}
