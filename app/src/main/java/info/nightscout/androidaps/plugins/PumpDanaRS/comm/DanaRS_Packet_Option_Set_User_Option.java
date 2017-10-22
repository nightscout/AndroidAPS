package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_Option_Set_User_Option extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Option_Set_User_Option.class);

    private int error;

    public DanaRS_Packet_Option_Set_User_Option() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION;
        if (Config.logDanaMessageDetail) {
            log.debug("Setting user settings");
        }
    }

    @Override
    public byte[] getRequestParams() {
        DanaRPump pump = DanaRPump.getInstance();

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
        if (Config.logDanaMessageDetail) {
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
