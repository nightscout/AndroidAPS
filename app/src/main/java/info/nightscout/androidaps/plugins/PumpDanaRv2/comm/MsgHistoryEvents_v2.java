package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.utils.DateUtil;

public class MsgHistoryEvents_v2 extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryEvents_v2.class);
    public boolean done;

    public MsgHistoryEvents_v2(Date from) {
        SetCommand(0xE003);
        GregorianCalendar gfrom = new GregorianCalendar();
        gfrom.setTimeInMillis(from.getTime());
        AddParamDate(gfrom);
        done = false;
    }

    public MsgHistoryEvents_v2() {
        SetCommand(0xE003);
        AddParamByte((byte) 0);
        AddParamByte((byte) 1);
        AddParamByte((byte) 1);
        AddParamByte((byte) 0);
        AddParamByte((byte) 0);
        done = false;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        byte recordCode = (byte) intFromBuff(bytes, 0, 1);

        // Last record
        if (recordCode == (byte) 0xFF) {
            done = true;
            return;
        }

        Date datetime = dateTimeSecFromBuff(bytes, 1);             // 6 bytes
        int param1 = intFromBuff(bytes, 7, 2);
        int param2 = intFromBuff(bytes, 9, 2);

        switch (recordCode) {
            case 1:
                log.debug("EVENT TEMPSTART (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Ratio: " + param1 + "% Duration: " + param2 + "min");
                break;
            case 2:
                log.debug("EVENT TEMPSTOP (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime));
                break;
            case 3:
                log.debug("EVENT EXTENDEDSTART (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                break;
            case 4:
                log.debug("EVENT EXTENDEDSTOP (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                break;
            case 5:
                log.debug("EVENT BOLUS (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                break;
            case 6:
                log.debug("EVENT DUALBOLUS (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                break;
            case 7:
                log.debug("EVENT DUALEXTENDEDSTART (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                break;
            case 8:
                log.debug("EVENT DUALEXTENDEDSTOP (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                break;
            case 9:
                log.debug("EVENT SUSPENDON (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime));
                break;
            case 10:
                log.debug("EVENT SUSPENDOFF (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime));
                break;
            case 11:
                log.debug("EVENT REFILL (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Amount: " + param1 + "U");
                break;
            case 12:
                log.debug("EVENT PRIME (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Amount: " + param1 + "U");
                break;
            case 13:
                log.debug("EVENT PROFILECHANGE (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " No: " + param1 + "U CurrentRate: " + param2 + "U/h");
                break;
            case 14:
                log.debug("EVENT CARBS (" + recordCode + ") " + DateUtil.dateAndTimeString(datetime) + " Carbs: " + param1 + "g");
                break;
            default:
                log.debug("Event: " + recordCode + " " + DateUtil.dateAndTimeString(datetime) + " Param1: " + param1 + " Param2: " + param2);
                break;
        }

        return;
    }
}
