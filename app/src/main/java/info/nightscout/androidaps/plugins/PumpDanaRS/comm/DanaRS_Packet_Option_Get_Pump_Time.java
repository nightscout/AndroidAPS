package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_Option_Get_Pump_Time extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Option_Get_Pump_Time.class);

    public DanaRS_Packet_Option_Get_Pump_Time() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME;
        if (Config.logDanaMessageDetail) {
            log.debug("Requesting pump time");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int year = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int month = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int day = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int hour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int min = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int sec = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        Date time = new Date(100 + year, month - 1, day, hour, min, sec);
        DanaRPump.getInstance().pumpTime = time;

        if (Config.logDanaMessageDetail) {
            log.debug("Pump time " + time.toLocaleString());
        }
    }

    @Override
    public String getFriendlyName() {
        return "OPTION__GET_PUMP_TIME";
    }
}
