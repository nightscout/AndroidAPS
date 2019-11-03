package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Option_Set_Pump_Time extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private Date date;
    public int error;

    public DanaRS_Packet_Option_Set_Pump_Time() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME;
    }

    public DanaRS_Packet_Option_Set_Pump_Time(Date date) {
        this();
        this.date = date;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Setting pump time " + date.toLocaleString());
        }
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[6];
        request[0] = (byte) ((date.getYear() - 100) & 0xff);
        request[1] = (byte) ((date.getMonth() + 1) & 0xff);
        request[2] = (byte) (date.getDate() & 0xff);
        request[3] = (byte) (date.getHours() & 0xff);
        request[4] = (byte) (date.getMinutes() & 0xff);
        request[5] = (byte) (date.getSeconds() & 0xff);
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
        return "OPTION__SET_PUMP_TIME";
    }
}
