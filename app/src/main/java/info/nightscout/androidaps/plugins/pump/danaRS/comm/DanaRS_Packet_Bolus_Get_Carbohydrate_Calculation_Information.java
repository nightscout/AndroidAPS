package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION;
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
        dataSize = 2;
        int carbs = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.currentCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (error != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Result: " + error);
            log.debug("Carbs: " + carbs);
            log.debug("Current CIR: " + pump.currentCIR);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION";
    }
}
