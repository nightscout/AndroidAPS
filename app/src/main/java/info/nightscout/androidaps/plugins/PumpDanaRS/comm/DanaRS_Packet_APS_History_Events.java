package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.ConfigBuilder.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;

public class DanaRS_Packet_APS_History_Events extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_APS_History_Events.class);

    private int year = 0;
    private int month = 0;
    private int day = 0;
    private int hour = 0;
    private int min = 0;
    private int sec = 0;

    public static boolean done;
    private static int totalCount;

    public static long lastEventTimeLoaded = 0;

    DanaRS_Packet_APS_History_Events() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS;
        done = false;
        totalCount = 0;
    }

    public DanaRS_Packet_APS_History_Events(long from) {
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
        byte recordCode = (byte) intFromBuff(data, 0, 1);

        // Last record
        if (recordCode == (byte) 0xFF) {
            done = true;
            log.debug("Last record received");
            return;
        }

        Date datetime = dateTimeSecFromBuff(data, 1);             // 6 bytes
        int param1 = ((intFromBuff(data, 7, 1) << 8) & 0xFF00) + (intFromBuff(data, 8, 1) & 0xFF);
        int param2 = ((intFromBuff(data, 9, 1) << 8) & 0xFF00) + (intFromBuff(data, 10, 1) & 0xFF);

        TemporaryBasal temporaryBasal = new TemporaryBasal().date(datetime.getTime()).source(Source.PUMP).pumpId(datetime.getTime());

        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = datetime.getTime();
        extendedBolus.source = Source.PUMP;
        extendedBolus.pumpId = datetime.getTime();

        DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.findDetailedBolusInfo(datetime.getTime());
        if (detailedBolusInfo == null) {
            log.debug("Detailed bolus info not found for " + datetime.toLocaleString());
            detailedBolusInfo = new DetailedBolusInfo();
        } else {
            log.debug("Detailed bolus info found: " + detailedBolusInfo);
        }
        detailedBolusInfo.date = datetime.getTime();
        detailedBolusInfo.source = Source.PUMP;
        detailedBolusInfo.pumpId = datetime.getTime();

        String status;

        switch (recordCode) {
            case DanaRPump.TEMPSTART:
                log.debug("EVENT TEMPSTART (" + recordCode + ") " + datetime.toLocaleString() + " Ratio: " + param1 + "% Duration: " + param2 + "min");
                temporaryBasal.percentRate = param1;
                temporaryBasal.durationInMinutes = param2;
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
                status = "TEMPSTART " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.TEMPSTOP:
                log.debug("EVENT TEMPSTOP (" + recordCode + ") " + datetime.toLocaleString());
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
                status = "TEMPSTOP " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.EXTENDEDSTART:
                log.debug("EVENT EXTENDEDSTART (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                extendedBolus.insulin = param1 / 100d;
                extendedBolus.durationInMinutes = param2;
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "EXTENDEDSTART " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.EXTENDEDSTOP:
                log.debug("EVENT EXTENDEDSTOP (" + recordCode + ") " + datetime.toLocaleString() + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "EXTENDEDSTOP " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.BOLUS:
                detailedBolusInfo.insulin = param1 / 100d;
                boolean newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
                log.debug((newRecord ? "**NEW** " : "") + "EVENT BOLUS (" + recordCode + ") " + datetime.toLocaleString() + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                DetailedBolusInfoStorage.remove(detailedBolusInfo.date);
                status = "BOLUS " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.DUALBOLUS:
                detailedBolusInfo.insulin = param1 / 100d;
                newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
                log.debug((newRecord ? "**NEW** " : "") + "EVENT DUALBOLUS (" + recordCode + ") " + datetime.toLocaleString() + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                DetailedBolusInfoStorage.remove(detailedBolusInfo.date);
                status = "DUALBOLUS " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.DUALEXTENDEDSTART:
                log.debug("EVENT DUALEXTENDEDSTART (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                extendedBolus.insulin = param1 / 100d;
                extendedBolus.durationInMinutes = param2;
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "DUALEXTENDEDSTART " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.DUALEXTENDEDSTOP:
                log.debug("EVENT DUALEXTENDEDSTOP (" + recordCode + ") " + datetime.toLocaleString() + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "DUALEXTENDEDSTOP " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.SUSPENDON:
                log.debug("EVENT SUSPENDON (" + recordCode + ") " + datetime.toLocaleString());
                status = "SUSPENDON " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.SUSPENDOFF:
                log.debug("EVENT SUSPENDOFF (" + recordCode + ") " + datetime.toLocaleString());
                status = "SUSPENDOFF " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.REFILL:
                log.debug("EVENT REFILL (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + param1 / 100d + "U");
                status = "REFILL " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.PRIME:
                log.debug("EVENT PRIME (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + param1 / 100d + "U");
                status = "PRIME " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.PROFILECHANGE:
                log.debug("EVENT PROFILECHANGE (" + recordCode + ") " + datetime.toLocaleString() + " No: " + param1 + " CurrentRate: " + (param2 / 100d) + "U/h");
                status = "PROFILECHANGE " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.CARBS:
                DetailedBolusInfo emptyCarbsInfo = new DetailedBolusInfo();
                emptyCarbsInfo.carbs = param1;
                emptyCarbsInfo.date = datetime.getTime();
                emptyCarbsInfo.source = Source.PUMP;
                emptyCarbsInfo.pumpId = datetime.getTime();
                newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(emptyCarbsInfo);
                log.debug((newRecord ? "**NEW** " : "") + "EVENT CARBS (" + recordCode + ") " + datetime.toLocaleString() + " Carbs: " + param1 + "g");
                status = "CARBS " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.PRIMECANNULA:
                log.debug("EVENT PRIMECANNULA(" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + param1 / 100d + "U");
                status = "PRIMECANNULA " + DateUtil.timeString(datetime);
                break;
            default:
                log.debug("Event: " + recordCode + " " + datetime.toLocaleString() + " Param1: " + param1 + " Param2: " + param2);
                status = "UNKNOWN " + DateUtil.timeString(datetime);
                break;
        }

        if (datetime.getTime() > lastEventTimeLoaded)
            lastEventTimeLoaded = datetime.getTime();

        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.processinghistory) + ": " + status));
    }

    @Override
    public String getFriendlyName() {
        return "APS_HISTORY_EVENTS";
    }
}
