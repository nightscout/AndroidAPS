package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;

/**
 * Application: GGC - GNU Gluco Control
 * Plug-in: Pump Tool (support for Pump devices)
 * <p>
 * See AUTHORS for copyright information.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * <p>
 * Filename: BolusDTO Description: Bolus DTO
 * <p>
 * Author: Andy {andy@atech-software.com}
 */
@Deprecated
public class TemporaryBasalRateDTO extends PumpTimeStampedRecord {

    private Float amount;
    private TBRUnit unit = TBRUnit.Percent; // percent, U
    private Integer duration; // min


    // private ATechDate aTechDate;

    public TemporaryBasalRateDTO() {
        // this.decimalPrecission = 2;
    }


    public Integer getDuration() {
        return duration;
    }


    public void setDuration(Integer duration) {
        this.duration = duration;
    }


    private String getDurationString() {
        int minutes = this.duration;

        int h = minutes / 60;

        minutes -= (h * 60);

        return StringUtil.getLeadingZero(h, 2) + ":" + StringUtil.getLeadingZero(minutes, 2);
    }


    public String getValue() {
        float val = amount;
        String sign = "";

        if (val < 0) {
            sign = "-";
            val *= -1;
        }

        return String.format("TBR_VALUE=%s%s;TBR_UNIT=%s;DURATION=%s", sign, getFormattedDecimal(val), getUnit()
            .getDescription(), getDurationString());
    }


    public Float getAmount() {
        return amount;
    }


    public void setAmount(Float amount) {
        this.amount = amount;
    }


    public TBRUnit getUnit() {
        return unit;
    }


    public void setUnit(TBRUnit unit) {
        this.unit = unit;
    }

    public enum TBRUnit {
        Percent("%"), //
        Unit("U");

        String unit;


        TBRUnit(String unit) {
            this.unit = unit;
        }


        public String getDescription() {
            return unit;
        }
    }

}
