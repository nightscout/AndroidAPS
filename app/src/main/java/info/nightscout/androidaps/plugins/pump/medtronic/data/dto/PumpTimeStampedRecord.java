package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;

/**
 * Created by andy on 6/2/18.
 */
@Deprecated
public class PumpTimeStampedRecord {

    // protected LocalDateTime localDateTime;
    protected int decimalPrecission = 2;
    public long atechDateTime;


    public long getAtechDateTime() {
        return this.atechDateTime;
    }


    public void setAtechDateTime(long atechDateTime) {
        this.atechDateTime = atechDateTime;
    }


    // public LocalDateTime getLocalDateTime() {
    // return localDateTime;
    // }
    //
    //
    // public void setLocalDateTime(LocalDateTime ATechDate) {
    // this.localDateTime = ATechDate;
    // }

    public String getFormattedDecimal(double value) {
        return StringUtil.getFormatedValueUS(value, this.decimalPrecission);
    }

}
