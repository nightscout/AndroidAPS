package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;

public class MsgHistoryEvents_v2 extends MessageBase {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    public boolean done;

    public static long lastEventTimeLoaded = 0;

    public MsgHistoryEvents_v2() {
        this(0);
    }

    public MsgHistoryEvents_v2(long from) {
        SetCommand(0xE003);

        if (from > DateUtil.now()) {
            log.debug("Asked to load from the future");
            from = 0;
        }

        if (from == 0) {
            AddParamByte((byte) 0);
            AddParamByte((byte) 1);
            AddParamByte((byte) 1);
            AddParamByte((byte) 0);
            AddParamByte((byte) 0);
        } else {
            GregorianCalendar gfrom = new GregorianCalendar();
            gfrom.setTimeInMillis(from);
            AddParamDate(gfrom);
        }

        done = false;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        byte recordCode = (byte) intFromBuff(bytes, 0, 1);

        // Last record
        if (recordCode == (byte) 0xFF) {
            done = true;
            return;
        }

        long datetime = dateTimeSecFromBuff(bytes, 1);             // 6 bytes
        int param1 = intFromBuff(bytes, 7, 2);
        int param2 = intFromBuff(bytes, 9, 2);

        TemporaryBasal temporaryBasal = new TemporaryBasal()
                .date(datetime)
                .source(Source.PUMP)
                .pumpId(datetime);

        ExtendedBolus extendedBolus = new ExtendedBolus()
                .date(datetime)
                .source(Source.PUMP)
                .pumpId(datetime);

        String status = "";

        switch (recordCode) {
            case DanaRPump.TEMPSTART:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT TEMPSTART (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Ratio: " + param1 + "% Duration: " + param2 + "min");
                temporaryBasal.percentRate = param1;
                temporaryBasal.durationInMinutes = param2;
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
                status = "TEMPSTART " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.TEMPSTOP:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT TEMPSTOP (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime));
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
                status = "TEMPSTOP " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.EXTENDEDSTART:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT EXTENDEDSTART (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                extendedBolus.insulin = param1 / 100d;
                extendedBolus.durationInMinutes = param2;
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "EXTENDEDSTART " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.EXTENDEDSTOP:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT EXTENDEDSTOP (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "EXTENDEDSTOP " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.BOLUS:
                DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.INSTANCE.findDetailedBolusInfo(datetime, param1 / 100d);
                if (detailedBolusInfo == null) {
                    detailedBolusInfo = new DetailedBolusInfo();
                }
                detailedBolusInfo.date = datetime;
                detailedBolusInfo.source = Source.PUMP;
                detailedBolusInfo.pumpId = datetime;

                detailedBolusInfo.insulin = param1 / 100d;
                boolean newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug((newRecord ? "**NEW** " : "") + "EVENT BOLUS (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                status = "BOLUS " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.DUALBOLUS:
                detailedBolusInfo = DetailedBolusInfoStorage.INSTANCE.findDetailedBolusInfo(datetime, param1 / 100d);
                if (detailedBolusInfo == null) {
                    detailedBolusInfo = new DetailedBolusInfo();
                }
                detailedBolusInfo.date = datetime;
                detailedBolusInfo.source = Source.PUMP;
                detailedBolusInfo.pumpId = datetime;

                detailedBolusInfo.insulin = param1 / 100d;
                newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug((newRecord ? "**NEW** " : "") + "EVENT DUALBOLUS (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                status = "DUALBOLUS " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.DUALEXTENDEDSTART:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT DUALEXTENDEDSTART (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                extendedBolus.insulin = param1 / 100d;
                extendedBolus.durationInMinutes = param2;
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "DUALEXTENDEDSTART " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.DUALEXTENDEDSTOP:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT DUALEXTENDEDSTOP (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                status = "DUALEXTENDEDSTOP " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.SUSPENDON:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT SUSPENDON (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")");
                status = "SUSPENDON " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.SUSPENDOFF:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT SUSPENDOFF (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")");
                status = "SUSPENDOFF " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.REFILL:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT REFILL (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100d + "U");
                status = "REFILL " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.PRIME:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT PRIME (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100d + "U");
                status = "PRIME " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.PROFILECHANGE:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("EVENT PROFILECHANGE (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " No: " + param1 + " CurrentRate: " + (param2 / 100d) + "U/h");
                status = "PROFILECHANGE " + DateUtil.timeString(datetime);
                break;
            case DanaRPump.CARBS:
                DetailedBolusInfo emptyCarbsInfo = new DetailedBolusInfo();
                emptyCarbsInfo.carbs = param1;
                emptyCarbsInfo.date = datetime;
                emptyCarbsInfo.source = Source.PUMP;
                emptyCarbsInfo.pumpId = datetime;
                newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(emptyCarbsInfo, false);
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug((newRecord ? "**NEW** " : "") + "EVENT CARBS (" + recordCode + ") " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Carbs: " + param1 + "g");
                status = "CARBS " + DateUtil.timeString(datetime);
                break;
            default:
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("Event: " + recordCode + " " + DateUtil.dateAndTimeFullString(datetime) + " (" + datetime + ")" + " Param1: " + param1 + " Param2: " + param2);
                status = "UNKNOWN " + DateUtil.timeString(datetime);
                break;
        }

        if (datetime > lastEventTimeLoaded)
            lastEventTimeLoaded = datetime;

        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.processinghistory) + ": " + status));
    }
}
