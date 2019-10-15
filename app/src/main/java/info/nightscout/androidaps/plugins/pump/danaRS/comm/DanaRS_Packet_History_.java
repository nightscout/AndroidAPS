package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus;
import info.nightscout.androidaps.utils.DateUtil;

public abstract class DanaRS_Packet_History_ extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int year = 0;
    private int month = 0;
    private int day = 0;
    private int hour = 0;
    private int min = 0;
    private int sec = 0;

    public boolean done;
    public int totalCount;

    public DanaRS_Packet_History_() {
        super();
        done = false;
        totalCount = 0;
    }

    public DanaRS_Packet_History_(long from) {
        this();
        GregorianCalendar cal = new GregorianCalendar();
        if (from != 0)
            cal.setTimeInMillis(from);
        else
            cal.set(2000, 0, 1, 0, 0, 0);
        year = cal.get(Calendar.YEAR) - 1900 - 100;
        month = cal.get(Calendar.MONTH) + 1;
        day = cal.get(Calendar.DAY_OF_MONTH);
        hour = cal.get(Calendar.HOUR_OF_DAY);
        min = cal.get(Calendar.MINUTE);
        sec = cal.get(Calendar.SECOND);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Loading event history from: " + new Date(cal.getTimeInMillis()).toLocaleString());
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
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("History end. Code: " + error + " Success: " + (error == 0x00));
        } else if (data.length == 5) {
            int dataIndex = DATA_START;
            int dataSize = 1;
            error = byteArrayToInt(getBytes(data, dataIndex, dataSize));
            done = true;

            dataIndex += dataSize;
            dataSize = 2;
            totalCount = byteArrayToInt(getBytes(data, dataIndex, dataSize));
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("History end. Code: " + error + " Success: " + (error == 0x00) + " Toatal count: " + totalCount);
        } else {
            int recordCode = byteArrayToInt(getBytes(data, DATA_START, 1));
            int historyYear = byteArrayToInt(getBytes(data, DATA_START + 1, 1));
            int historyMonth = byteArrayToInt(getBytes(data, DATA_START + 2, 1));
            int historyDay = byteArrayToInt(getBytes(data, DATA_START + 3, 1));
            int historyHour = byteArrayToInt(getBytes(data, DATA_START + 4, 1));
            double dailyBasal = (((data[DATA_START + 4] & 0xFF) << 8) + (data[DATA_START + 5] & 0xFF)) * 0.01d;
            int historyMinute = byteArrayToInt(getBytes(data, DATA_START + 5, 1));
            int historySecond = byteArrayToInt(getBytes(data, DATA_START + 6, 1));
            byte paramByte7 = (byte) historySecond;
            double dailyBolus = (((data[DATA_START + 6] & 0xFF) << 8) + (data[DATA_START + 7] & 0xFF)) * 0.01d;

            Date date = new Date(100 + historyYear, historyMonth - 1, historyDay);
            Date datetime = new Date(100 + historyYear, historyMonth - 1, historyDay, historyHour, historyMinute);
            Date datetimewihtsec = new Date(100 + historyYear, historyMonth - 1, historyDay, historyHour, historyMinute, historySecond);

            int historyCode = byteArrayToInt(getBytes(data, DATA_START + 7, 1));
            byte paramByte8 = (byte) historyCode;

            int value = ((data[DATA_START + 8] & 0xFF) << 8) + (data[DATA_START + 9] & 0xFF);

            if (L.isEnabled(L.PUMPCOMM))
                log.debug("History packet: " + recordCode + " Date: " + datetimewihtsec.toLocaleString() + " Code: " + historyCode + " Value: " + value);


            DanaRHistoryRecord danaRHistoryRecord = new DanaRHistoryRecord();

            danaRHistoryRecord.setBytes(data);
            // danaRHistoryRecord.recordCode is different from DanaR codes
            // set in switch for every type

            String messageType = "";

            switch (recordCode) {
                case 0x02:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_BOLUS;
                    danaRHistoryRecord.recordDate = datetime.getTime();
                    switch (0xF0 & paramByte8) {
                        case 0xA0:
                            danaRHistoryRecord.bolusType = "DS";
                            messageType += "DS bolus";
                            break;
                        case 0xC0:
                            danaRHistoryRecord.bolusType = "E";
                            messageType += "E bolus";
                            break;
                        case 0x80:
                            danaRHistoryRecord.bolusType = "S";
                            messageType += "S bolus";
                            break;
                        case 0x90:
                            danaRHistoryRecord.bolusType = "DE";
                            messageType += "DE bolus";
                            break;
                        default:
                            danaRHistoryRecord.bolusType = "None";
                            break;
                    }
                    danaRHistoryRecord.recordDuration = ((int) paramByte8 & 0x0F) * 60 + (int) paramByte7;
                    danaRHistoryRecord.recordValue = value * 0.01;
                    break;
                case 0x03:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_DAILY;
                    messageType += "dailyinsulin";
                    danaRHistoryRecord.recordDate = date.getTime();
                    danaRHistoryRecord.recordDailyBasal = dailyBasal;
                    danaRHistoryRecord.recordDailyBolus = dailyBolus;
                    break;
                case 0x04:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_PRIME;
                    messageType += "prime";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    danaRHistoryRecord.recordValue = value * 0.01;
                    break;
                case 0x05:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_REFILL;
                    messageType += "refill";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    danaRHistoryRecord.recordValue = value * 0.01;
                    break;
                case 0x0b:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_BASALHOUR;
                    messageType += "basal hour";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    danaRHistoryRecord.recordValue = value * 0.01;
                    break;
                case 0x99: ///// ????????? don't know the right code
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_TEMP_BASAL;
                    messageType += "tb";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    danaRHistoryRecord.recordValue = value * 0.01;
                    break;
                case 0x06:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_GLUCOSE;
                    messageType += "glucose";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    danaRHistoryRecord.recordValue = value;
                    break;
                case 0x07:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_CARBO;
                    messageType += "carbo";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    danaRHistoryRecord.recordValue = value;
                    break;
                case 0x0a:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_ALARM;
                    messageType += "alarm";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    String strAlarm = "None";
                    switch ((int) paramByte8) {
                        case 67:
                            strAlarm = "Check";
                            break;
                        case 79:
                            strAlarm = "Occlusion";
                            break;
                        case 66:
                            strAlarm = "Low Battery";
                            break;
                        case 83:
                            strAlarm = "Shutdown";
                            break;
                    }
                    danaRHistoryRecord.recordAlarm = strAlarm;
                    danaRHistoryRecord.recordValue = value * 0.01;
                    break;
                case 0x09:
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_SUSPEND;
                    messageType += "suspend";
                    danaRHistoryRecord.recordDate = datetimewihtsec.getTime();
                    String strRecordValue = "Off";
                    if ((int) paramByte8 == 79)
                        strRecordValue = "On";
                    danaRHistoryRecord.stringRecordValue = strRecordValue;
                    break;
            }

            MainApp.getDbHelper().createOrUpdate(danaRHistoryRecord);

            RxBus.INSTANCE.send(new EventDanaRSyncStatus(DateUtil.dateAndTimeString(danaRHistoryRecord.recordDate) + " " + messageType));

        }
    }
}
