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

    void createTBRrecord(Date eventDate, int percent, int duration, long record_id) {

        final TemporaryBasal temporaryBasal = new TemporaryBasal();
        temporaryBasal.date = eventDate.getTime();
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

        final DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.date = eventDate.getTime();
        detailedBolusInfo.source = Source.PUMP;
        detailedBolusInfo.pumpId = record_id;
        detailedBolusInfo.insulin = insulin;
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
    }
}
