package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class JournalEntryExerciseMarkerPumpEvent extends TimeStampedRecord {

    public JournalEntryExerciseMarkerPumpEvent() {
    }


    @Override
    public int getLength() {
        return 8;
    }


    @Override
    public String getShortTypeName() {
        return "Exercise Marker";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
