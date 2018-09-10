package info.nightscout.androidaps.plugins.PumpInsight.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

/**
 * Created by jamorham on 27/01/2018.
 * <p>
 * Write to the History Log
 */

class HistoryLogAdapter {
    private Logger log = LoggerFactory.getLogger(L.PUMP);

    private static final long MAX_TIME_DIFFERENCE = 61000;

    void createTBRrecord(Date eventDate, int percent, int duration, long record_id) {

        TemporaryBasal temporaryBasal = new TemporaryBasal().date(eventDate.getTime());

        final TemporaryBasal temporaryBasalFromHistory = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(eventDate.getTime());

        if (temporaryBasalFromHistory == null) {
            if (L.isEnabled(L.PUMP))
                log.debug("Create new TBR: " + eventDate + " " + percent + " " + duration);
        } else {
            if (L.isEnabled(L.PUMP))
                log.debug("Loaded existing TBR record: " + temporaryBasalFromHistory.toString());
            if (Math.abs(eventDate.getTime() - temporaryBasalFromHistory.date) < MAX_TIME_DIFFERENCE) {
                if (temporaryBasalFromHistory.source != Source.PUMP) {
                    if (temporaryBasalFromHistory.percentRate == percent) {
                        if (L.isEnabled(L.PUMP))
                            log.debug("Things seem to match: %" + percent);
                        temporaryBasal = temporaryBasalFromHistory;
                        MainApp.getDbHelper().delete(temporaryBasalFromHistory);
                    } else {
                        if (L.isEnabled(L.PUMP))
                            log.debug("This record has different percent rates: " + temporaryBasalFromHistory.percentRate + " vs us: " + percent);
                    }
                } else {
                    if (L.isEnabled(L.PUMP))
                        log.debug("This record is already a pump record!");
                }
            } else {
                if (L.isEnabled(L.PUMP))
                    log.debug("Time difference too great! : " + (eventDate.getTime() - temporaryBasalFromHistory.date));
            }
        }

        temporaryBasal.source(Source.PUMP)
                .pumpId(record_id)
                .percent(percent)
                .duration(duration);

        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
    }

    void createExtendedBolusRecord(Date eventDate, double insulin, int durationInMinutes, long record_id) {

        // TODO trap items below minimum period

        final ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = eventDate.getTime();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.PUMP;
        extendedBolus.pumpId = record_id;

        TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
    }

    void createStandardBolusRecord(Date eventDate, double insulin, long record_id) {

        //DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.findDetailedBolusInfo(eventDate.getTime());

        // TODO do we need to do the same delete + insert that we are doing for temporary basals here too?

        final DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.date = eventDate.getTime();
        detailedBolusInfo.source = Source.PUMP;
        detailedBolusInfo.pumpId = record_id;
        detailedBolusInfo.insulin = insulin;
        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);
    }
}
