package info.nightscout.androidaps.plugins.PumpMedtronic.comm.history;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.Expose;

import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.HexDump;
import info.nightscout.androidaps.plugins.PumpCommon.utils.StringUtil;

/**
 * Application: GGC - GNU Gluco Control
 * Plug-in: GGC PlugIn Base (base class for all plugins)
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
 * Filename: MinimedHistoryRecord Description: Minimed History Record.
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public abstract class MedtronicHistoryEntry implements MedtronicHistoryEntryInterface {

    protected List<Byte> rawData;

    protected static DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss");
    public static final Logger LOG = LoggerFactory.getLogger(MedtronicHistoryEntry.class);

    protected int[] sizes = new int[3];

    protected byte[] head;
    protected byte[] datetime;
    protected byte[] body;

    // protected LocalDateTime dateTime;

    @Expose
    public String DT;

    @Expose
    public long atechDateTime;

    @Expose
    protected Map<String, Object> decodedData;


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
            sb.append(HexDump.toHexStringDisplayable(this.head));
        }

        if (datetime != null) {
            sb.append(", datetime=");
            sb.append(HexDump.toHexStringDisplayable(this.datetime));
        }

        if (body != null) {
            sb.append(", body=");
            sb.append(HexDump.toHexStringDisplayable(this.body));
        }

        sb.append(", rawData=");
        sb.append(HexDump.toHexStringDisplayable(this.rawData));
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


    // public LocalDateTime getLocalDateTime() {
    // return this.dateTime;
    // }
    //
    //
    // public void setLocalDateTime(LocalDateTime atdate) {
    // this.dateTime = atdate;
    // // this.DT = atdate.toString(dateTimeFormatter);
    // }

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
            return "HistoryRecord: head=[" + HexDump.toHexStringDisplayable(this.head) + "]";
        }
    }

    // if we extend to CGMS this need to be changed back
    // public abstract PumpHistoryEntryType getEntryType();

}
