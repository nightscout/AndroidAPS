package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
