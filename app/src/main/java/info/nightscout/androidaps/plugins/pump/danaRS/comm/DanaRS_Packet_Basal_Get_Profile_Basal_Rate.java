package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Basal_Get_Profile_Basal_Rate extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int profileNumber;

    DanaRPump pump = DanaRPump.getInstance();

    public DanaRS_Packet_Basal_Get_Profile_Basal_Rate() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE;
    }

    // 0 - 4
    public DanaRS_Packet_Basal_Get_Profile_Basal_Rate(int profileNumber) {
        this();
        this.profileNumber = profileNumber;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Requesting basal rates for profile " + profileNumber);
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
        int dataSize = 2;

        if (pump.pumpProfiles == null) pump.pumpProfiles = new double[4][];
        pump.pumpProfiles[profileNumber] = new double[24];
        for (int i = 0, size = 24; i < size; i++) {
            pump.pumpProfiles[profileNumber][i] = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
            dataIndex += dataSize;
            dataSize = 2;
        }
        if (L.isEnabled(L.PUMPCOMM)) {
            for (int index = 0; index < 24; index++)
                log.debug("Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + pump.pumpProfiles[profileNumber][index]);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__GET_PROFILE_BASAL_RATE";
    }

}
