package info.nightscout.androidaps.plugins.PumpInsight.history;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;

/**
 * Created by jamorham on 27/01/2018.
 *
 * Write to the History Log
 *
 */

class HistoryLogAdapter {

    private static final long MAX_TIME_DIFFERENCE = 61000;

    private static void log(String msg) {
        android.util.Log.e("HISTORYLOG", msg);
    }

    void createTBRrecord(Date eventDate, int percent, int duration, long record_id) {

        TemporaryBasal temporaryBasal = new TemporaryBasal(eventDate.getTime());

        final TemporaryBasal temporaryBasalFromHistory = MainApp.getConfigBuilder().getTempBasalFromHistory(eventDate.getTime());

        if (temporaryBasalFromHistory == null) {
            log("Create new TBR: " + eventDate + " " + percent + " " + duration);
        } else {
            log("Loaded existing TBR record: " + temporaryBasalFromHistory.toString());
            if (Math.abs(eventDate.getTime() - temporaryBasalFromHistory.date) < MAX_TIME_DIFFERENCE) {
                if (temporaryBasalFromHistory.source != Source.PUMP) {
                    if (temporaryBasalFromHistory.percentRate == percent) {
                        log("Things seem to match: %" + percent);
                        temporaryBasal = temporaryBasalFromHistory;
                        MainApp.getDbHelper().delete(temporaryBasalFromHistory);
                    } else {
                        log("This record has different percent rates: " + temporaryBasalFromHistory.percentRate + " vs us: " + percent);
                    }
                } else {
                    log("This record is already a pump record!");
                }
            } else {
                log("Time difference too great! : " + (eventDate.getTime() - temporaryBasalFromHistory.date));
            }
        }

        temporaryBasal.source = Source.PUMP;
        temporaryBasal.pumpId = record_id;
        temporaryBasal.percentRate = percent;
        temporaryBasal.durationInMinutes = duration;

        MainApp.getConfigBuilder().addToHistoryTempBasal(temporaryBasal);
    }

    void createExtendedBolusRecord(Date eventDate, double insulin, int durationInMinutes, long record_id) {

        // TODO trap items below minimum period

        final ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = eventDate.getTime();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.PUMP;
        extendedBolus.pumpId = record_id;

        MainApp.getConfigBuilder().addToHistoryExtendedBolus(extendedBolus);
    }

    void createStandardBolusRecord(Date eventDate, double insulin, long record_id) {

        //DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.findDetailedBolusInfo(eventDate.getTime());

        // TODO do we need to do the same delete + insert that we are doing for temporary basals here too?

        final DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.date = eventDate.getTime();
        detailedBolusInfo.source = Source.PUMP;
        detailedBolusInfo.pumpId = record_id;
        detailedBolusInfo.insulin = insulin;
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
    }
}
