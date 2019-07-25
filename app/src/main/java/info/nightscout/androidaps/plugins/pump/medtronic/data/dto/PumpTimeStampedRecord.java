package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;

/**
 * Created by andy on 6/2/18.
 */
public class PumpTimeStampedRecord {

    protected int decimalPrecission = 2;
    public long atechDateTime;


    public long getAtechDateTime() {
        return this.atechDateTime;
    }


    public void setAtechDateTime(long atechDateTime) {
        this.atechDateTime = atechDateTime;
    }


    public String getFormattedDecimal(double value) {
        return StringUtil.getFormatedValueUS(value, this.decimalPrecission);
    }

}
