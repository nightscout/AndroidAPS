package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;

public class TempBasalProcessDTO {

    public PumpHistoryEntry itemOne;
    public PumpHistoryEntry itemTwo;

    public Operation processOperation = Operation.None;

    public int getDuration() {
        if (itemTwo == null) {
            TempBasalPair tbr = (TempBasalPair) itemOne.getDecodedDataEntry("Object");
            return tbr.getDurationMinutes();
        } else {
            int difference = DateTimeUtil.getATechDateDiferenceAsMinutes(itemOne.atechDateTime, itemTwo.atechDateTime);
            return difference;
        }
    }


    public enum Operation {
        None,
        Add,
        Edit
    }


}
