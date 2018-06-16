package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.PumpCommon.utils.StringUtil;

/**
 * Created by andy on 6/2/18.
 */
@Deprecated
public class PumpTimeStampedRecord {

    protected LocalDateTime localDateTime;
    protected int decimalPrecission = 2;


    public void setLocalDateTime(LocalDateTime ATechDate) {
        this.localDateTime = ATechDate;
    }


    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }


    public String getFormattedDecimal(double value) {
        return StringUtil.getFormatedValueUS(value, this.decimalPrecission);
    }

}
