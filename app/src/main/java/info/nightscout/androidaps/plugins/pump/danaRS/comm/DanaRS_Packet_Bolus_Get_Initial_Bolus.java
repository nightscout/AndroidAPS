package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Get_Initial_Bolus extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private double initialBolusValue01;
    private double initialBolusValue02;
    private double initialBolusValue03;
    double initialBolusValue04;

    public DanaRS_Packet_Bolus_Get_Initial_Bolus() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 2;
        initialBolusValue01 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        initialBolusValue02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        initialBolusValue03 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        initialBolusValue04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
        if (initialBolusValue01 == 0d && initialBolusValue02 == 0d && initialBolusValue03 == 0d && initialBolusValue04 == 0d)
            failed = true;
        else
            failed = false;

        if (L.isEnabled(L.PUMPCOMM)) {
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
