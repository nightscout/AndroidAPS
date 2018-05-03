package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;

public class MsgHistoryEvents_v2 extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryEvents_v2.class);
    public boolean done;

    public static long lastEventTimeLoaded = 0;

    public MsgHistoryEvents_v2(long from) {
        SetCommand(0xE003);
        GregorianCalendar gfrom = new GregorianCalendar();
        gfrom.setTimeInMillis(from);
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

        TemporaryBasal temporaryBasal = new TemporaryBasal()
                .date(datetime.getTime())
                .source(Source.PUMP)
                .pumpId(datetime.getTime());

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

        String status = "";

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
            default:
                log.debug("Event: " + recordCode + " " + datetime.toLocaleString() + " Param1: " + param1 + " Param2: " + param2);
                status = "UNKNOWN " + DateUtil.timeString(datetime);
                break;
        }

        if (datetime.getTime() > lastEventTimeLoaded)
            lastEventTimeLoaded = datetime.getTime();

        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.processinghistory) + ": " + status));
    }
}
