package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_Bolus_Get_Calculation_Information extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Get_Calculation_Information.class);

    public DanaRS_Packet_Bolus_Get_Calculation_Information() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION;
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        int error = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        double currentBG = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        int carbohydrate = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.currentTarget = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.currentCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.currentCF = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 1;
        pump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (pump.units == DanaRPump.UNITS_MMOL) {
            pump.currentCF = pump.currentCF / 100d;
            pump.currentTarget = pump.currentTarget / 100d;
            currentBG = currentBG / 100d;
        }
        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + error);
            log.debug("Pump units: " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current BG: " + currentBG);
            log.debug("Carbs: " + carbohydrate);
            log.debug("Current target: " + pump.currentTarget);
            log.debug("Current CIR: " + pump.currentCIR);
            log.debug("Current CF: " + pump.currentCF);
            log.debug("Pump IOB: " + pump.iob);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_CALCULATION_INFORMATION";
    }
}
