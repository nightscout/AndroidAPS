package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Bolus_Get_CIR_CF_Array extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Bolus_Get_CIR_CF_Array() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        int language = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.morningCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        int cir02 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.afternoonCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        int cir04 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.eveningCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        int cir06 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.nightCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        double cf02, cf04, cf06;

        if (pump.units == DanaRPump.UNITS_MGDL) {
            dataIndex += dataSize;
            dataSize = 2;
            pump.morningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            cf02 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            pump.afternoonCF = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            cf04 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            pump.eveningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            cf06 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            pump.nightCF = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        } else {
            dataIndex += dataSize;
            dataSize = 2;
            pump.morningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

            dataIndex += dataSize;
            dataSize = 2;
            cf02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

            dataIndex += dataSize;
            dataSize = 2;
            pump.afternoonCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

            dataIndex += dataSize;
            dataSize = 2;
            cf04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

            dataIndex += dataSize;
            dataSize = 2;
            pump.eveningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

            dataIndex += dataSize;
            dataSize = 2;
            cf06 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

            dataIndex += dataSize;
            dataSize = 2;
            pump.nightCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
        }
        if (pump.units < 0 || pump.units > 1)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Language: " + language);
            log.debug("Pump units: " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("Current pump morning CIR: " + pump.morningCIR);
            log.debug("Current pump morning CF: " + pump.morningCF);
            log.debug("Current pump afternoon CIR: " + pump.afternoonCIR);
            log.debug("Current pump afternoon CF: " + pump.afternoonCF);
            log.debug("Current pump evening CIR: " + pump.eveningCIR);
            log.debug("Current pump evening CF: " + pump.eveningCF);
            log.debug("Current pump night CIR: " + pump.nightCIR);
            log.debug("Current pump night CF: " + pump.nightCF);
            log.debug("cir02: " + cir02);
            log.debug("cir04: " + cir04);
            log.debug("cir06: " + cir06);
            log.debug("cf02: " + cf02);
            log.debug("cf04: " + cf04);
            log.debug("cf06: " + cf06);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_CIR_CF_ARRAY";
    }
}
