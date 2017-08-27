package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.ConfigBuilder.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;

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

        TemporaryBasal temporaryBasal = new TemporaryBasal();
        temporaryBasal.date = datetime.getTime();
        temporaryBasal.source = Source.PUMP;
        temporaryBasal.pumpId = datetime.getTime();

        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = datetime.getTime();
        extendedBolus.source = Source.PUMP;
        extendedBolus.pumpId = datetime.getTime();

        DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.findDetailedBolusInfo(datetime.getTime());
        if (detailedBolusInfo == null) {
            log.debug("DetailedBolusInfo not found for " + datetime.toLocaleString());
            detailedBolusInfo = new DetailedBolusInfo();
        }
        detailedBolusInfo.date = datetime.getTime();
        detailedBolusInfo.source = Source.PUMP;
        detailedBolusInfo.pumpId = datetime.getTime();

        switch (recordCode) {
            case DanaRPump.TEMPSTART:
                log.debug("EVENT TEMPSTART (" + recordCode + ") " + datetime.toLocaleString() + " Ratio: " + param1 + "% Duration: " + param2 + "min");
                temporaryBasal.percentRate = param1;
                temporaryBasal.durationInMinutes = param2;
                MainApp.getConfigBuilder().addToHistoryTempBasal(temporaryBasal);
                break;
            case DanaRPump.TEMPSTOP:
                log.debug("EVENT TEMPSTOP (" + recordCode + ") " + datetime.toLocaleString());
                MainApp.getConfigBuilder().addToHistoryTempBasal(temporaryBasal);
                break;
            case DanaRPump.EXTENDEDSTART:
                log.debug("EVENT EXTENDEDSTART (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                extendedBolus.insulin = param1 / 100d;
                extendedBolus.durationInMinutes = param2;
                MainApp.getConfigBuilder().addToHistoryExtendedBolus(extendedBolus);
                break;
            case DanaRPump.EXTENDEDSTOP:
                log.debug("EVENT EXTENDEDSTOP (" + recordCode + ") " + datetime.toLocaleString() + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                MainApp.getConfigBuilder().addToHistoryExtendedBolus(extendedBolus);
                break;
            case DanaRPump.BOLUS:
                detailedBolusInfo.insulin = param1 / 100d;
                boolean newRecord = MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                log.debug((newRecord ? "**NEW** " : "") + "EVENT BOLUS (" + recordCode + ") " + datetime.toLocaleString() + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                DetailedBolusInfoStorage.remove(detailedBolusInfo.date);
                break;
            case DanaRPump.DUALBOLUS:
                detailedBolusInfo.insulin = param1 / 100d;
                newRecord = MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                log.debug((newRecord ? "**NEW** " : "") + "EVENT DUALBOLUS (" + recordCode + ") " + datetime.toLocaleString() + " Bolus: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                DetailedBolusInfoStorage.remove(detailedBolusInfo.date);
                break;
            case DanaRPump.DUALEXTENDEDSTART:
                log.debug("EVENT DUALEXTENDEDSTART (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + (param1 / 100d) + "U Duration: " + param2 + "min");
                extendedBolus.insulin = param1 / 100d;
                extendedBolus.durationInMinutes = param2;
                MainApp.getConfigBuilder().addToHistoryExtendedBolus(extendedBolus);
                break;
            case DanaRPump.DUALEXTENDEDSTOP:
                log.debug("EVENT DUALEXTENDEDSTOP (" + recordCode + ") " + datetime.toLocaleString() + " Delivered: " + (param1 / 100d) + "U RealDuration: " + param2 + "min");
                MainApp.getConfigBuilder().addToHistoryExtendedBolus(extendedBolus);
                break;
            case DanaRPump.SUSPENDON:
                log.debug("EVENT SUSPENDON (" + recordCode + ") " + datetime.toLocaleString());
                break;
            case DanaRPump.SUSPENDOFF:
                log.debug("EVENT SUSPENDOFF (" + recordCode + ") " + datetime.toLocaleString());
                break;
            case DanaRPump.REFILL:
                log.debug("EVENT REFILL (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + param1 / 100d + "U");
                break;
            case DanaRPump.PRIME:
                log.debug("EVENT PRIME (" + recordCode + ") " + datetime.toLocaleString() + " Amount: " + param1 / 100d + "U");
                break;
            case DanaRPump.PROFILECHANGE:
                log.debug("EVENT PROFILECHANGE (" + recordCode + ") " + datetime.toLocaleString() + " No: " + param1 + " CurrentRate: " + (param2 / 100d) + "U/h");
                break;
            case DanaRPump.CARBS:
                DetailedBolusInfo emptyCarbsInfo = new DetailedBolusInfo();
                emptyCarbsInfo.carbs = param1;
                emptyCarbsInfo.date = datetime.getTime();
                emptyCarbsInfo.source = Source.PUMP;
                emptyCarbsInfo.pumpId = datetime.getTime();
                newRecord = MainApp.getConfigBuilder().addToHistoryTreatment(emptyCarbsInfo);
                log.debug((newRecord ? "**NEW** " : "") + "EVENT CARBS (" + recordCode + ") " + datetime.toLocaleString() + " Carbs: " + param1 + "g");
                break;
            default:
                log.debug("Event: " + recordCode + " " + datetime.toLocaleString() + " Param1: " + param1 + " Param2: " + param2);
                break;
        }

        if (datetime.getTime() > lastEventTimeLoaded)
            lastEventTimeLoaded = datetime.getTime();

    }
}
