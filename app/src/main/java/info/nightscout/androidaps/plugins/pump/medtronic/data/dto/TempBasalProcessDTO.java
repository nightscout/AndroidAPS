package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;

public class TempBasalProcessDTO {

    public PumpHistoryEntry itemOne;
    public PumpHistoryEntry itemTwo;

    public Operation processOperation = Operation.None;


    public static enum Operation {
        None,
        Add,
        Edit
    }


}
