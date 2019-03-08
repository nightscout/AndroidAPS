package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Bolus_Get_Extended_Bolus_State extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Bolus_Get_Extended_Bolus_State() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        int error = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01;

        dataIndex += dataSize;
        dataSize = 1;
        pump.extendedBolusMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize)) * 30;

        dataIndex += dataSize;
        dataSize = 2;
        pump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        pump.extendedBolusSoFarInMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.extendedBolusDeliveredSoFar = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
        if (error != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Result: " + error);
            log.debug("Is extended bolus running: " + pump.isExtendedInProgress);
            log.debug("Extended bolus running: " + pump.extendedBolusAbsoluteRate + " U/h");
            log.debug("Extended bolus duration: " + pump.extendedBolusMinutes + " min");
            log.debug("Extended bolus so far: " + pump.extendedBolusSoFarInMinutes + " min");
            log.debug("Extended bolus delivered so far: " + pump.extendedBolusDeliveredSoFar + " U");
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_EXTENDED_BOLUS_STATE";
    }
}
