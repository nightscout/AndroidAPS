package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

abstract public class Record {

    protected MedtronicDeviceType model;
    protected byte recordOp;
    // protected int length;
    protected int foundAtOffset;
    protected byte[] rawbytes = new byte[0];


    // protected String recordTypeName = this.getClass().getSimpleName();

    public Record() {

    }


    protected static int asUINT8(byte b) {
        return (b < 0) ? b + 256 : b;
    }


    public String getRecordTypeName() {
        return this.getClass().getSimpleName();
    }


    public String getShortTypeName() {
        return this.getClass().getSimpleName();
    }


    public void setPumpModel(MedtronicDeviceType model) {
        this.model = model;
    }


    public int getFoundAtOffset() {
        return foundAtOffset;
    }


    public boolean parseWithOffset(byte[] data, MedtronicDeviceType model, int foundAtOffset) {
        // keep track of where the record was found for later analysis
        this.foundAtOffset = foundAtOffset;
        if (data == null) {
            return false;
        }
        if (data.length < 1) {
            return false;
        }
        recordOp = data[0];
        boolean didParse = parseFrom(data, model);
        if (didParse) {
            captureRawBytes(data);
        }
        return didParse;
    }


    public void captureRawBytes(byte[] data) {
        this.rawbytes = new byte[getLength()];
        System.arraycopy(data, 0, this.rawbytes, 0, getLength() - 1);
    }


    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        return true;
    }


    public PumpTimeStamp getTimestamp() {
        return new PumpTimeStamp();
    }


    public int getLength() {
        return 1;
    }


    public byte getRecordOp() {
        return recordOp;
    }


    public Bundle dictionaryRepresentation() {
        Bundle rval = new Bundle();
        writeToBundle(rval);
        return rval;
    }


    public boolean readFromBundle(Bundle in) {
        // length is determined at instantiation
        // record type name is "static"
        // opcode has already been read.
        return true;
    }


    public boolean isLargerFormat() {
        return MedtronicDeviceType.isLargerFormat(model);
    }


    public void writeToBundle(Bundle in) {
        in.putInt("length", getLength());
        in.putInt("foundAtOffset", foundAtOffset);
        in.putInt("_opcode", recordOp);
        in.putString("_type", getRecordTypeName());
        in.putString("_stype", getShortTypeName());
        in.putByteArray("rawbytes", rawbytes);
    }


    public byte[] getRawbytes() {
        return rawbytes;
    }


    public abstract boolean isAAPSRelevant();

}
