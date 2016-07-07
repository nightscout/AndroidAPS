package info.nightscout.androidaps.plugins.DanaR.comm;

import com.j256.ormlite.dao.Dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.HistoryRecord;

public class MsgHistoryAll extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryAll.class);

    public MsgHistoryAll() {
        SetCommand(0x41F2);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        byte recordCode = (byte) intFromBuff(bytes, 0, 1);
        Date date = dateFromBuff(bytes, 1);                     // 3 bytes
        Date datetime = dateTimeFromBuff(bytes, 1);             // 5 bytes
        Date datetimewihtsec = dateTimeSecFromBuff(bytes, 1);   // 6 bytes

        byte paramByte5 = (byte) intFromBuff(bytes, 4, 1);
        byte paramByte6 = (byte) intFromBuff(bytes, 5, 1);
        byte paramByte7 = (byte) intFromBuff(bytes, 6, 1);
        byte paramByte8 = (byte) intFromBuff(bytes, 7, 1);
        double value = (double) intFromBuff(bytes, 8, 2);

        HistoryRecord historyRecord = new HistoryRecord();

        historyRecord.setRecordCode(recordCode);
        historyRecord.setBytes(bytes);

        switch (recordCode) {
            case DanaRRecordTypes.RECORD_TYPE_BOLUS:
                historyRecord.setRecordDate(datetime);
                switch (0xF0 & paramByte8) {
                    case 0xA0:
                        historyRecord.setBolusType("DS");
                        break;
                    case 0xC0:
                        historyRecord.setBolusType("E");
                        break;
                    case 0x80:
                        historyRecord.setBolusType("S");
                        break;
                    case 0x90:
                        historyRecord.setBolusType("DE");
                        break;
                    default:
                        historyRecord.setBolusType("None");
                        break;
                }
                historyRecord.setRecordDuration(((int) paramByte8 & 0x0F) * 60 + (int) paramByte7);
                historyRecord.setRecordValue(value * 0.01);
                break;
            case DanaRRecordTypes.RECORD_TYPE_DAILY:
                historyRecord.setRecordDate(date);
                historyRecord.setRecordDailyBasal((double) ((int) paramByte5 * 0xFF + (int) paramByte6) * 0.01);
                historyRecord.setRecordDailyBolus((double) ((int) paramByte7 * 0xFF + (int) paramByte8) / 0.01);
                break;
            case DanaRRecordTypes.RECORD_TYPE_PRIME:
            case DanaRRecordTypes.RECORD_TYPE_ERROR:
            case DanaRRecordTypes.RECORD_TYPE_REFILL:
            case DanaRRecordTypes.RECORD_TYPE_BASALHOUR:
            case DanaRRecordTypes.RECORD_TYPE_TB:
                historyRecord.setRecordDate(datetimewihtsec);
                historyRecord.setRecordValue(value * 0.01);
                break;
            case DanaRRecordTypes.RECORD_TYPE_GLUCOSE:
            case DanaRRecordTypes.RECORD_TYPE_CARBO:
                historyRecord.setRecordDate(datetimewihtsec);
                historyRecord.setRecordValue(value);
                break;
            case DanaRRecordTypes.RECORD_TYPE_ALARM:
                historyRecord.setRecordDate(datetimewihtsec);
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
                historyRecord.setRecordAlarm(strAlarm);
                historyRecord.setRecordValue(value * 0.01);
                break;
            case DanaRRecordTypes.RECORD_TYPE_SUSPEND:
                historyRecord.setRecordDate(datetimewihtsec);
                String strRecordValue = "Off";
                if ((int) paramByte8 == 79)
                    strRecordValue = "On";
                historyRecord.setStringRecordValue(strRecordValue);
                break;
        }

        try {
            Dao<HistoryRecord, String> daoHistoryRecords = MainApp.getDbHelper().getDaoHistory();
            daoHistoryRecords.createIfNotExists(historyRecord);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return;
    }
}
