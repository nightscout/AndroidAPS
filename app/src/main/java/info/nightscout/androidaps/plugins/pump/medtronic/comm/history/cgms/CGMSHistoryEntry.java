package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryEntry;

/**
 * Created by andy on 27.03.15.
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


    @Override
    public String getToStringStart() {
        return "CGMSHistoryEntry [type=" + entryType.name() + " [" + getOpCode() + ", 0x"
            + ByteUtil.getCorrectHexValue(getOpCode()) + "]";
    }

}
