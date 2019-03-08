package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Etc_Set_History_Save extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int historyType;
    private int historyYear;
    private int historyMonth;
    private int historyDate;
    private int historyHour;
    private int historyMinute;
    private int historySecond;
    private int historyCode;
    private int historyValue;

    public DanaRS_Packet_Etc_Set_History_Save() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_ETC__SET_HISTORY_SAVE;
    }

    public DanaRS_Packet_Etc_Set_History_Save(int historyType, int historyYear, int historyMonth, int historyDate, int historyHour, int historyMinute, int historySecond, int historyCode, int historyValue) {
        this();
        this.historyType = historyType;
        this.historyYear = historyYear;
        this.historyMonth = historyMonth;
        this.historyDate = historyDate;
        this.historyHour = historyHour;
        this.historyMinute = historyMinute;
        this.historySecond = historySecond;
        this.historyCode = historyCode;
        this.historyValue = historyValue;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[10];
        request[0] = (byte) (historyType & 0xff);
        request[1] = (byte) (historyYear & 0xff);
        request[2] = (byte) (historyMonth & 0xff);
        request[3] = (byte) (historyDate & 0xff);
        request[4] = (byte) (historyHour & 0xff);
        request[5] = (byte) (historyMinute & 0xff);
        request[6] = (byte) (historySecond & 0xff);
        request[7] = (byte) (historyCode & 0xff);
        request[8] = (byte) (historyValue & 0xff);
        request[9] = (byte) ((historyValue >>> 8) & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (error != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Result: " + error);
        }
    }

    @Override
    public String getFriendlyName() {
        return "ETC__SET_HISTORY_SAVE";
    }
}
