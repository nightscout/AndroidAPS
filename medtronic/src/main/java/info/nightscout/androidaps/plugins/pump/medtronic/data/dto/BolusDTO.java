package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpBolusType;

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

public class BolusDTO extends PumpTimeStampedRecord {

    @Expose
    private Double requestedAmount;
    @Expose
    private Double deliveredAmount;
    @Expose
    private Double immediateAmount; // when Multiwave this is used
    @Expose
    private Integer duration;
    @Expose
    private PumpBolusType bolusType;
    private Double insulinOnBoard;


    public BolusDTO() {
        // this.decimalPrecission = 2;
    }


    public Double getRequestedAmount() {
        return requestedAmount;
    }


    public void setRequestedAmount(Double requestedAmount) {
        this.requestedAmount = requestedAmount;
    }


    public Double getDeliveredAmount() {
        return deliveredAmount;
    }


    public void setDeliveredAmount(Double deliveredAmount) {
        this.deliveredAmount = deliveredAmount;
    }


    public Integer getDuration() {
        return duration;
    }


    public void setDuration(Integer duration) {
        this.duration = duration;
    }


    public PumpBolusType getBolusType() {
        return bolusType;
    }


    public void setBolusType(PumpBolusType bolusType) {
        this.bolusType = bolusType;
    }


    public Double getInsulinOnBoard() {
        return insulinOnBoard;
    }


    public void setInsulinOnBoard(Double insulinOnBoard) {
        this.insulinOnBoard = insulinOnBoard;
    }


    private String getDurationString() {
        int minutes = this.duration;

        int h = minutes / 60;

        minutes -= (h * 60);

        return StringUtil.getLeadingZero(h, 2) + ":" + StringUtil.getLeadingZero(minutes, 2);
    }


    public String getValue() {
        if ((bolusType == PumpBolusType.Normal) || (bolusType == PumpBolusType.Audio)) {
            return getFormattedDecimal(this.deliveredAmount);
        } else if (bolusType == PumpBolusType.Extended) {
            return String.format("AMOUNT_SQUARE=%s;DURATION=%s", getFormattedDecimal(this.deliveredAmount),
                    getDurationString());
        } else {
            return String.format("AMOUNT=%s;AMOUNT_SQUARE=%s;DURATION=%s", getFormattedDecimal(this.immediateAmount),
                    getFormattedDecimal(this.deliveredAmount), getDurationString());
        }
    }


    public String getDisplayableValue() {
        String value = getValue();

        value = value.replace("AMOUNT_SQUARE=", "Amount Square: ");
        value = value.replace("AMOUNT=", "Amount: ");
        value = value.replace("DURATION=", "Duration: ");

        return value;
    }


    public Double getImmediateAmount() {
        return immediateAmount;
    }


    public void setImmediateAmount(Double immediateAmount) {
        this.immediateAmount = immediateAmount;
    }


    public String getFormattedDecimal(double value) {
        return StringUtil.getFormatedValueUS(value, 2);
    }


    public String getBolusKey() {
        return "Bolus_" + this.bolusType.name();

    }


    @Override
    public String toString() {
        return "BolusDTO [type=" + bolusType.name() + ", " + getValue() + "]";
    }

}
