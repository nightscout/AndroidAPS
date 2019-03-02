package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeAudioBolusPumpEvent extends TimeStampedRecord {

    public ChangeAudioBolusPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Audio Bolus";
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
