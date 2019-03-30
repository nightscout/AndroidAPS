package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Option_Get_Pump_Time extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Option_Get_Pump_Time() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME;
        if (L.isEnabled(L.PUMPCOMM)) {
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
        DanaRPump.getInstance().pumpTime = time.getTime();

        if ( year == month && month == day && day == hour && hour == min && min == sec && sec == 1)
            failed = true;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump time " + time.toLocaleString());
        }
    }

    @Override
    public String getFriendlyName() {
        return "OPTION__GET_PUMP_TIME";
    }
}
