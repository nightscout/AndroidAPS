package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Review_Bolus_Avg extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Review_Bolus_Avg.class);

    public DanaRS_Packet_Review_Bolus_Avg() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BOLUS_AVG;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 2;
        double bolusAvg03 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double bolusAvg07 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double bolusAvg14 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double bolusAvg21 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double bolusAvg28 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
        if (Config.logDanaMessageDetail) {
            log.debug("Bolus average 3d: " + bolusAvg03 + " U");
            log.debug("Bolus average 7d: " + bolusAvg07 + " U");
            log.debug("Bolus average 14d: " + bolusAvg14 + " U");
            log.debug("Bolus average 21d: " + bolusAvg21 + " U");
            log.debug("Bolus average 28d: " + bolusAvg28 + " U");
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__BOLUS_AVG";
    }
}
