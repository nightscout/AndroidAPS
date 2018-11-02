package info.nightscout.androidaps.plugins.PumpInsight.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.T;

/**
 * Created by jamorham on 27/01/2018.
 * <p>
 * Write to the History Log
 */

class HistoryLogAdapter {
    private Logger log = LoggerFactory.getLogger(L.PUMP);

    private static final long MAX_TIME_DIFFERENCE = T.secs(61).msecs();

    void createTBRrecord(long eventDate, int percent, int duration, long record_id) {

        TemporaryBasal temporaryBasal = new TemporaryBasal().date(eventDate);

        final TemporaryBasal temporaryBasalFromHistory = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(eventDate);

        if (temporaryBasalFromHistory == null) {
            if (L.isEnabled(L.PUMP))
                log.debug("Create new TBR: " + eventDate + " " + percent + " " + duration);
        } else {
            if (L.isEnabled(L.PUMP))
                log.debug("Loaded existing TBR record: " + temporaryBasalFromHistory.toString());
            if (Math.abs(eventDate - temporaryBasalFromHistory.date) < MAX_TIME_DIFFERENCE) {
                if (temporaryBasalFromHistory.source != Source.PUMP) {
                    if (temporaryBasalFromHistory.percentRate == percent) {
                        if (L.isEnabled(L.PUMP))
                            log.debug("Things seem to match: %" + percent);
                        temporaryBasal = temporaryBasalFromHistory;
                        String _id = temporaryBasal._id;
                        if (NSUpload.isIdValid(_id)) {
                            NSUpload.removeCareportalEntryFromNS(_id);
                        } else {
                            UploadQueue.removeID("dbAdd", _id);
                        }
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
                    log.debug("Time difference too big! : " + (eventDate - temporaryBasalFromHistory.date));
            }
        }

        temporaryBasal.source(Source.PUMP)
                .pumpId(record_id)
                .percent(percent)
                .duration(duration);

        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
    }

    void createExtendedBolusRecord(long eventDate, double insulin, int durationInMinutes, long record_id) {

        final ExtendedBolus extendedBolusFromHistory = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(eventDate);

        if (extendedBolusFromHistory == null) {
            if (L.isEnabled(L.PUMP))
                log.debug("Create new EB: " + eventDate + " " + insulin + " " + durationInMinutes);
        } else {
            if (L.isEnabled(L.PUMP))
                log.debug("Loaded existing EB record: " + extendedBolusFromHistory.log());
            if (Math.abs(eventDate - extendedBolusFromHistory.date) < MAX_TIME_DIFFERENCE) {
                if (extendedBolusFromHistory.source != Source.PUMP) {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Date seem to match: " + DateUtil.dateAndTimeFullString(eventDate));
                    String _id = extendedBolusFromHistory._id;
                    if (NSUpload.isIdValid(_id)) {
                        NSUpload.removeCareportalEntryFromNS(_id);
                    } else {
                        UploadQueue.removeID("dbAdd", _id);
                    }
                    MainApp.getDbHelper().delete(extendedBolusFromHistory);
                } else {
                    if (L.isEnabled(L.PUMP))
                        log.debug("This record is already a pump record!");
                }
            } else {
                if (L.isEnabled(L.PUMP))
                    log.debug("Time difference too big! : " + (eventDate - extendedBolusFromHistory.date));
            }
        }

        // TODO trap items below minimum period

        // TODO (mike) find and remove ending record with Source.USER

        ExtendedBolus extendedBolus = new ExtendedBolus()
                .date(eventDate)
                .insulin(insulin)
                .durationInMinutes(durationInMinutes)
                .source(Source.PUMP)
                .pumpId(record_id);

        if (ProfileFunctions.getInstance().getProfile(extendedBolus.date) != null) // actual basal rate is needed for absolute rate calculation
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
    }

    void createStandardBolusRecord(long eventDate, double insulin, long record_id) {

        //DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.findDetailedBolusInfo(eventDate.getTime());

        // TODO do we need to do the same delete + insert that we are doing for temporary basals here too?

        final DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.date = eventDate;
        detailedBolusInfo.source = Source.PUMP;
        detailedBolusInfo.pumpId = record_id;
        detailedBolusInfo.insulin = insulin;
        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);
    }
}
