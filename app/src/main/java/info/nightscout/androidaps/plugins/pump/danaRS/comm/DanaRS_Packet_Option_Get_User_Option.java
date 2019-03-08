package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Option_Get_User_Option extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);


    public DanaRS_Packet_Option_Get_User_Option() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Requesting user settings");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        pump.timeDisplayType = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.buttonScrollOnOff = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.beepAndAlarm = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.lcdOnTimeSec = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.backlightOnTimeSec = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.selectedLanguage = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.shutdownHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.lowReservoirRate = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.cannulaVolume = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.refillAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int selectableLanguage1 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int selectableLanguage2 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int selectableLanguage3 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int selectableLanguage4 = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int selectableLanguage5 = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        // Pump's screen on time can't be less than 5
        failed = pump.lcdOnTimeSec < 5 ? true : false;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("timeDisplayType: " + pump.timeDisplayType);
            log.debug("buttonScrollOnOff: " + pump.buttonScrollOnOff);
            log.debug("beepAndAlarm: " + pump.beepAndAlarm);
            log.debug("lcdOnTimeSec: " + pump.lcdOnTimeSec);
            log.debug("backlightOnTimeSec: " + pump.backlightOnTimeSec);
            log.debug("selectedLanguage: " + pump.selectedLanguage);
            log.debug("Pump units: " + (pump.units == DanaRPump.UNITS_MGDL ? "MGDL" : "MMOL"));
            log.debug("shutdownHour: " + pump.shutdownHour);
            log.debug("lowReservoirRate: " + pump.lowReservoirRate);
            log.debug("refillAmount: " + pump.refillAmount);
            log.debug("selectableLanguage1: " + selectableLanguage1);
            log.debug("selectableLanguage2: " + selectableLanguage2);
            log.debug("selectableLanguage3: " + selectableLanguage3);
            log.debug("selectableLanguage4: " + selectableLanguage4);
            log.debug("selectableLanguage5: " + selectableLanguage5);
        }
    }

    @Override
    public String getFriendlyName() {
        return "OPTION__GET_USER_OPTION";
    }

}
