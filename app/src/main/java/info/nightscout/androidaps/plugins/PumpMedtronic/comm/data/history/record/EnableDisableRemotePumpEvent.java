package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;


import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class EnableDisableRemotePumpEvent extends TimeStampedRecord {
    public EnableDisableRemotePumpEvent() {
    }

    @Override
    public int getLength() {
        return 21;
    }

    @Override
    public String getShortTypeName() {
        return "Toggle Remote";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
