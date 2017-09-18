package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import com.cozmo.danar.util.BleCommandUtil;

public abstract class DanaRS_Packet_History_ extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_History_.class);

    private int year = 0;
    private int month = 0;
    private int day = 0;
    private int hour = 0;
    private int min = 0;
    private int sec = 0;

    public static long lastEventTimeLoaded = 0;

    public boolean done;
    public int totalCount;

    public DanaRS_Packet_History_() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BOLUS;
        done = false;
        totalCount = 0;
    }

    public DanaRS_Packet_History_(Date from) {
        this();
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(from);
        year = cal.get(Calendar.YEAR) - 1900 - 100;
        month = cal.get(Calendar.MONTH) + 1;
        day = cal.get(Calendar.DAY_OF_MONTH);
        hour = cal.get(Calendar.HOUR_OF_DAY);
        min = cal.get(Calendar.MINUTE);
        sec = cal.get(Calendar.SECOND);
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[6];
        request[0] = (byte) (year & 0xff);
        request[1] = (byte) (month & 0xff);
        request[2] = (byte) (day & 0xff);
        request[3] = (byte) (hour & 0xff);
        request[4] = (byte) (min & 0xff);
        request[5] = (byte) (sec & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int error;
        totalCount = 0;
        if (data.length == 3) {
            int dataIndex = DATA_START;
            int dataSize = 1;
            error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
            done = true;
            log.debug("History end. Code: " + error + " Success: " + (error == 0x00));
        } else if (data.length == 5) {
            int dataIndex = DATA_START;
            int dataSize = 1;
            error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
            done = true;

            dataIndex += dataSize;
            dataSize = 2;
            totalCount = byteArrayToInt(getBytes(data, dataIndex, dataSize));
            log.debug("History end. Code: " + error + " Success: " + (error == 0x00) + " Toatal count: " + totalCount);
        } else {
            int dataIndex = DATA_START;
            int dataSize = 1;
            int historyType = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 1;
            int historyYear = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 1;
            int historyMonth = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 1;
            int historyDay = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 1;
            int historyHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 1;
            int historyMinute = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 1;
            int historySecond = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            Date date = new Date(100 + historyYear, historyMonth - 1, historyDay, historyHour, historyMinute, historySecond);
            lastEventTimeLoaded = date.getTime();

            dataIndex += dataSize;
            dataSize = 1;
            int historyCode = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            dataIndex += dataSize;
            dataSize = 2;
            int historyValue = byteArrayToInt(getBytes(data, dataIndex, dataSize));

            log.debug("History packet: " + historyType + " Date: " + date.toLocaleString() + " Code: " + historyCode + " Value: " + historyValue);
        }
    }
}
