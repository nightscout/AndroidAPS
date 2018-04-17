package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Bolus_Get_Initial_Bolus extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Get_Initial_Bolus.class);

    public DanaRS_Packet_Bolus_Get_Initial_Bolus() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 2;
        double initialBolusValue01 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double initialBolusValue02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double initialBolusValue03 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        double initialBolusValue04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
        if (Config.logDanaMessageDetail) {
            log.debug("Initial bolus amount 01: " + initialBolusValue01);
            log.debug("Initial bolus amount 02: " + initialBolusValue02);
            log.debug("Initial bolus amount 03: " + initialBolusValue03);
            log.debug("Initial bolus amount 04: " + initialBolusValue04);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_BOLUS_RATE";
    }
}
