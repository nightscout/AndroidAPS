package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Option_Set_User_Option extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int error;

    public DanaRS_Packet_Option_Set_User_Option() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Setting user settings");
        }
    }

    @Override
    public byte[] getRequestParams() {
        DanaRPump pump = DanaRPump.getInstance();
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("UserOptions:" + (System.currentTimeMillis() - pump.lastConnection) / 1000 + " s ago"
                    + "\ntimeDisplayType:" + pump.timeDisplayType
                    + "\nbuttonScroll:" + pump.buttonScrollOnOff
                    + "\ntimeDisplayType:" + pump.timeDisplayType
                    + "\nlcdOnTimeSec:" + pump.lcdOnTimeSec
                    + "\nbacklight:" + pump.backlightOnTimeSec
                    + "\npumpUnits:" + pump.units
                    + "\nlowReservoir:" + pump.lowReservoirRate);
        byte[] request = new byte[13];
        request[0] = (byte) (pump.timeDisplayType & 0xff);
        request[1] = (byte) (pump.buttonScrollOnOff & 0xff);
        request[2] = (byte) (pump.beepAndAlarm & 0xff);
        request[3] = (byte) (pump.lcdOnTimeSec & 0xff);
        request[4] = (byte) (pump.backlightOnTimeSec & 0xff);
        request[5] = (byte) (pump.selectedLanguage & 0xff);
        request[6] = (byte) (pump.units & 0xff);
        request[7] = (byte) (pump.shutdownHour & 0xff);
        request[8] = (byte) (pump.lowReservoirRate & 0xff);
        request[9] = (byte) (pump.cannulaVolume & 0xff);
        request[10] = (byte) ((pump.cannulaVolume >>> 8) & 0xff);
        request[11] = (byte) (pump.refillAmount & 0xff);
        request[12] = (byte) ((pump.refillAmount >>> 8) & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        failed = error != 0;
        if (L.isEnabled(L.PUMPCOMM)) {
            if (error == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + error);
        }
    }

    @Override
    public String getFriendlyName() {
        return "OPTION__SET_USER_OPTION";
    }
}
