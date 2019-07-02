package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

import com.google.gson.annotations.Expose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 * <p>
 * Author: Andy {andy.rozman@gmail.com}
 */

public abstract class MedtronicHistoryEntry implements MedtronicHistoryEntryInterface {

    protected List<Byte> rawData;

    public static final Logger LOG = LoggerFactory.getLogger(MedtronicHistoryEntry.class);

    protected int[] sizes = new int[3];

    protected byte[] head;
    protected byte[] datetime;
    protected byte[] body;

    // protected LocalDateTime dateTime;

    public long id;

    @Expose
    public String DT;

    @Expose
    public Long atechDateTime;

    @Expose
    protected Map<String, Object> decodedData;

    public long phoneDateTime; // time on phone

    /**
     * Pump id that will be used with AAPS object (time * 1000 + historyType (max is FF = 255)
     */
    protected Long pumpId;

    /**
     * if history object is already linked to AAPS object (either Treatment, TempBasal or TDD (tdd's
     * are not actually
     * linked))
     */
    public boolean linked = false;

    /**
     * Linked object, see linked
     */
    public Object linkedObject = null;


    public void setLinkedObject(Object linkedObject) {
        this.linked = true;
        this.linkedObject = linkedObject;
    }


    public void setData(List<Byte> listRawData, boolean doNotProcess) {
        this.rawData = listRawData;

        // System.out.println("Head: " + sizes[0] + ", dates: " + sizes[1] +
        // ", body=" + sizes[2]);

        if (doNotProcess)
            return;

        head = new byte[getHeadLength() - 1];
        for (int i = 1; i < (getHeadLength()); i++) {
            head[i - 1] = listRawData.get(i);
        }

        if (getDateTimeLength() > 0) {
            datetime = new byte[getDateTimeLength()];

            for (int i = getHeadLength(), j = 0; j < getDateTimeLength(); i++, j++) {
                datetime[j] = listRawData.get(i);
            }
        }

        if (getBodyLength() > 0) {
            body = new byte[getBodyLength()];

            for (int i = (getHeadLength() + getDateTimeLength()), j = 0; j < getBodyLength(); i++, j++) {
                body[j] = listRawData.get(i);
            }

        }

    }


    public String getDateTimeString() {
        return this.DT == null ? "Unknown" : this.DT;
    }


    public String getDecodedDataAsString() {
        if (decodedData == null)
            if (isNoDataEntry())
                return "No data";
            else
                return "";
        else
            return decodedData.toString();
    }


    public boolean hasData() {
        return (decodedData != null) || (isNoDataEntry()) || getEntryTypeName().equals("UnabsorbedInsulin");
    }


    public boolean isNoDataEntry() {
        return (sizes[0] == 2 && sizes[1] == 5 && sizes[2] == 0);
    }


    public Map<String, Object> getDecodedData() {
        return this.decodedData;
    }


    public Object getDecodedDataEntry(String key) {
        return this.decodedData != null ? this.decodedData.get(key) : null;
    }


    public boolean hasDecodedDataEntry(String key) {
        return this.decodedData.containsKey(key);
    }


    public boolean showRaw() {
        return getEntryTypeName().equals("EndResultTotals");
    }


    public int getHeadLength() {
        return sizes[0];
    }


    public int getDateTimeLength() {
        return sizes[1];
    }


    public int getBodyLength() {
        return sizes[2];
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (this.DT == null) {
            LOG.error("DT is null. RawData={}", ByteUtil.getHex(this.rawData));
        }

        sb.append(getToStringStart());
        sb.append(", DT: " + (this.DT == null ? "null" : StringUtil.getStringInLength(this.DT, 19)));
        sb.append(", length=");
        sb.append(getHeadLength());
        sb.append(",");
        sb.append(getDateTimeLength());
        sb.append(",");
        sb.append(getBodyLength());
        sb.append("(");
        sb.append((getHeadLength() + getDateTimeLength() + getBodyLength()));
        sb.append(")");

        boolean hasData = hasData();

        if (hasData) {
            sb.append(", data=" + getDecodedDataAsString());
        }

        if (hasData && !showRaw()) {
            sb.append("]");
            return sb.toString();
        }

        if (head != null) {
            sb.append(", head=");
            sb.append(ByteUtil.shortHexString(this.head));
        }

        if (datetime != null) {
            sb.append(", datetime=");
            sb.append(ByteUtil.shortHexString(this.datetime));
        }

        if (body != null) {
            sb.append(", body=");
            sb.append(ByteUtil.shortHexString(this.body));
        }

        sb.append(", rawData=");
        sb.append(ByteUtil.shortHexString(this.rawData));
        sb.append("]");

        // sb.append(" DT: ");
        // sb.append(this.dateTime == null ? " - " : this.dateTime.toString("dd.MM.yyyy HH:mm:ss"));

        // sb.append(" Ext: ");

        return sb.toString();
    }


    public abstract int getOpCode();


    public abstract String getToStringStart();


    public List<Byte> getRawData() {
        return rawData;
    }


    public byte getRawDataByIndex(int index) {
        return rawData.get(index);
    }


    public int getUnsignedRawDataByIndex(int index) {
        return ByteUtil.convertUnsignedByteToInt(rawData.get(index));
    }


    public void setRawData(List<Byte> rawData) {
        this.rawData = rawData;
    }


    public byte[] getHead() {
        return head;
    }


    public void setHead(byte[] head) {
        this.head = head;
    }


    public byte[] getDatetime() {
        return datetime;
    }


    public void setDatetime(byte[] datetime) {
        this.datetime = datetime;
    }


    public byte[] getBody() {
        return body;
    }


    public void setBody(byte[] body) {
        this.body = body;
    }


    public void setAtechDateTime(long dt) {
        this.atechDateTime = dt;
        this.DT = DateTimeUtil.toString(this.atechDateTime);
    }


    public void addDecodedData(String key, Object value) {
        if (decodedData == null)
            decodedData = new HashMap<>();

        decodedData.put(key, value);
    }


    public String toShortString() {
        if (head == null) {
            return "Unidentified record. ";
        } else {
            return "HistoryRecord: head=[" + ByteUtil.shortHexString(this.head) + "]";
        }
    }

    public boolean containsDecodedData(String key) {
        if (decodedData == null)
            return false;

        return decodedData.containsKey(key);
    }

    // if we extend to CGMS this need to be changed back
    // public abstract PumpHistoryEntryType getEntryType();

}
