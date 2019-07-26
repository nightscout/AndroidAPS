package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryEntry;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */

public class CGMSHistoryEntry extends MedtronicHistoryEntry {

    private CGMSHistoryEntryType entryType;
    private Integer opCode; // this is set only when we have unknown entry...


    public CGMSHistoryEntryType getEntryType() {
        return entryType;
    }


    public void setEntryType(CGMSHistoryEntryType entryType) {
        this.entryType = entryType;

        this.sizes[0] = entryType.getHeadLength();
        this.sizes[1] = entryType.getDateLength();
        this.sizes[2] = entryType.getBodyLength();
    }


    @Override
    public String getEntryTypeName() {
        return this.entryType.name();
    }


    public void setData(List<Byte> listRawData, boolean doNotProcess) {
        if (this.entryType.schemaSet) {
            super.setData(listRawData, doNotProcess);
        } else {
            this.rawData = listRawData;
        }
    }


    @Override
    public int getDateLength() {
        return this.entryType.getDateLength();
    }


    @Override
    public int getOpCode() {
        if (opCode == null)
            return entryType.getOpCode();
        else
            return opCode;
    }


    public void setOpCode(Integer opCode) {
        this.opCode = opCode;
    }


    public boolean hasTimeStamp() {
        return (this.entryType.hasDate());
    }


    @Override
    public String getToStringStart() {

        return "CGMSHistoryEntry [type=" + StringUtils.rightPad(entryType.name(), 18) + " ["
                + StringUtils.leftPad("" + getOpCode(), 3) + ", 0x" + ByteUtil.getCorrectHexValue(getOpCode()) + "]";
    }


    public void setDateTime(LocalDateTime timeStamp, int getIndex) {

        setAtechDateTime(DateTimeUtil.toATechDate(timeStamp.plusMinutes(getIndex * 5)));

    }
}
