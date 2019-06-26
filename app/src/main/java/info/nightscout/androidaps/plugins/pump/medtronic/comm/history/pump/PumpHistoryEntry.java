package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import com.google.gson.annotations.Expose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryEntry;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 * <p>
 * Author: Andy {andy.rozman@gmail.com}
 */

public class PumpHistoryEntry extends MedtronicHistoryEntry {

    private static Logger LOG = LoggerFactory.getLogger(PumpHistoryEntry.class);

    @Expose
    private PumpHistoryEntryType entryType;
    private Integer opCode; // this is set only when we have unknown entry...
    private int offset;
    private String displayableValue = "";


    public PumpHistoryEntryType getEntryType() {
        return entryType;
    }


    public void setEntryType(PumpHistoryEntryType entryType) {
        this.entryType = entryType;

        this.sizes[0] = entryType.getHeadLength();
        this.sizes[1] = entryType.getDateLength();
        this.sizes[2] = entryType.getBodyLength();

        if (this.entryType != null && this.atechDateTime != null)
            setPumpId();
    }


    private void setPumpId() {
        this.pumpId = this.entryType.getCode() + (this.atechDateTime * 1000L);
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


    @Override
    public String getToStringStart() {
        return "PumpHistoryEntry [type=" + StringUtil.getStringInLength(entryType.name(), 20) + " ["
                + StringUtil.getStringInLength("" + getOpCode(), 3) + ", 0x"
                + ByteUtil.shortHexString((byte) getOpCode()) + "]";
    }


    public String toString() {
        Object object = this.getDecodedDataEntry("Object");

        if (object == null) {
            return super.toString();
        } else {
            return "PumpHistoryEntry [DT: " + DT + ", Object=" + object.toString() + "]";
        }
    }


    public int getOffset() {
        return offset;
    }


    public void setOffset(int offset) {
        this.offset = offset;
    }


    @Override
    public String getEntryTypeName() {
        return this.entryType.name();
    }


    @Override
    public int getDateLength() {
        return this.entryType.getDateLength();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof PumpHistoryEntry))
            return false;

        PumpHistoryEntry that = (PumpHistoryEntry) o;

        return entryType == that.entryType && //
                this.atechDateTime == that.atechDateTime; // && //
        // Objects.equals(this.decodedData, that.decodedData);
    }


    @Override
    public int hashCode() {
        return Objects.hash(entryType, opCode, offset);
    }


    // public boolean isAfter(LocalDateTime dateTimeIn) {
    // // LOG.debug("Entry: " + this.dateTime);
    // // LOG.debug("Datetime: " + dateTimeIn);
    // // LOG.debug("Item after: " + this.dateTime.isAfter(dateTimeIn));
    // return this.dateTime.isAfter(dateTimeIn);
    // }

    public boolean isAfter(long atechDateTime) {
        if (this.atechDateTime == null) {
            LOG.error("Date is null. Show object: " + toString());
            return false; // FIXME shouldn't happen
        }

        return atechDateTime < this.atechDateTime;
    }


    public void setDisplayableValue(String displayableValue) {
        this.displayableValue = displayableValue;
    }


    public String getDisplayableValue() {
        return displayableValue;
    }

    public static class Comparator implements java.util.Comparator<PumpHistoryEntry> {

        @Override
        public int compare(PumpHistoryEntry o1, PumpHistoryEntry o2) {
            int data = (int) (o2.atechDateTime - o1.atechDateTime);

            if (data != 0)
                return data;

            return o2.getEntryType().getCode() - o1.getEntryType().getCode();
        }
    }


    public Long getPumpId() {
        setPumpId();

        return pumpId;
    }

}
