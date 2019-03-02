package info.nightscout.androidaps.plugins.pump.medtronic.comm.data;

/**
 * Created by geoff on 5/13/15.
 * <p>
 * This class was taken from medtronic-android-uploader.
 * This class was written such that the constructors did all the work, which resulted
 * in annoyances such as exceptions during constructors.
 * <p>
 * TODO: This class needs to be revisited and probably rewritten. (2016-06-12)
 * <p>
 * <p>
 * <p>
 * Pete Schwamb
 *
 * @ps2 12:04
 * History entries will not reorder themselves, but some events do update, like dual wave bolus entries
 * It's like an append only log, and when a page is full, it rotates the page ids and starts appending to a new blank page
 * Darrell Wright
 * @beached 12:05
 * so the timestamp is entry creation not update
 * time
 * Pete Schwamb
 * @ps2 12:06
 * Yes, I don't think the timestamps ever change
 * <p>
 * <p>
 * GGW: TODO: examine src/ecc1/medtronic for better history parsing
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.joda.time.DateTime;

import android.os.Bundle;
import android.util.Log;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.CRC;
import info.nightscout.androidaps.plugins.pump.common.utils.HexDump;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.PumpTimeStamp;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.Record;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.RecordTypeEnum;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeFormat;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.IgnoredHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

@Deprecated
public class Page {

    private final static String TAG = "Page";
    private static final boolean DEBUG_PAGE = true;
    // protected PumpModel model;
    public static MedtronicDeviceType model = MedtronicDeviceType.Medtronic_522;
    public List<Record> mRecordList;
    private byte[] crc;
    private byte[] data;


    public Page() {
        this.model = MedtronicDeviceType.Unknown_Device;
        mRecordList = new ArrayList<>();
    }


    /*
     * attemptParseRecord will attempt to create a subclass of Record from the given
     * data and offset. It will return NULL if it fails. If it succeeds, the returned
     * subclass of Record can be examined for its length, so that the next attempt can be made.
     * 
     * TODO maybe try to change this, so that we can loose all the classes and using enum instead with full
     * configuration. Its something to think about for later versions -- Andy
     */
    public static <T extends Record> T attemptParseRecord(byte[] data, int offsetStart) {
        // no data?
        if (data == null) {
            return null;
        }
        // invalid offset?
        if (data.length < offsetStart) {
            return null;
        }
        // Log.d(TAG,String.format("checking for handler for record type 0x%02X at index %d",data[offsetStart],offsetStart));
        RecordTypeEnum en = RecordTypeEnum.fromByte(data[offsetStart]);
        T record = en.getRecordClassInstance(model);
        if (record != null) {
            // have to do this to set the record's opCode
            byte[] tmpData = new byte[data.length];
            System.arraycopy(data, offsetStart, tmpData, 0, data.length - offsetStart);
            boolean didParse = record.parseWithOffset(tmpData, model, offsetStart);
            if (!didParse) {
                Log.e(
                    TAG,
                    String.format("attemptParseRecord: class %s (opcode 0x%02X) failed to parse at offset %d",
                        record.getShortTypeName(), data[offsetStart], offsetStart));
            }
        }
        return record;
    }


    public static DateTime parseSimpleDate(byte[] data, int offset) {
        DateTime timeStamp = null;
        int seconds = 0;
        int minutes = 0;
        int hour = 0;
        // int high = data[0] >> 4;
        int low = data[0 + offset] & 0x1F;
        // int year_high = data[1] >> 4;
        int mhigh = (data[0 + offset] & 0xE0) >> 4;
        int mlow = (data[1 + offset] & 0x80) >> 7;
        int month = mhigh + mlow;
        int dayOfMonth = low + 1;
        // python code says year is data[1] & 0x0F, but that will cause problem in 2016.
        // Hopefully, the remaining bits are part of the year...
        int year = data[1 + offset] & 0x3F;
        /*
         * Log.w(TAG, String.format("Attempting to create DateTime from: %04d-%02d-%02d %02d:%02d:%02d",
         * year + 2000, month, dayOfMonth, hour, minutes, seconds));
         */
        try {
            timeStamp = new DateTime(year + 2000, month, dayOfMonth, hour, minutes, seconds);
        } catch (org.joda.time.IllegalFieldValueException e) {
            // Log.e(TAG,"Illegal DateTime field");
            // e.printStackTrace();
            return null;
        }
        return timeStamp;
    }


    public static void discoverRecords(byte[] data) {
        int i = 0;
        boolean done = false;

        ArrayList<Integer> keyLocations = new ArrayList();
        while (!done) {
            RecordTypeEnum en = RecordTypeEnum.fromByte(data[i]);
            if (en != RecordTypeEnum.Null) {
                keyLocations.add(i);
                Log.v(TAG, String.format("Possible record of type %s found at index %d", en, i));
            }
            /*
             * DateTime ts = parseSimpleDate(data,i);
             * if (ts != null) {
             * if (ts.year().get() == 2015) {
             * Log.w(TAG, String.format("Possible simple date at index %d", i));
             * }
             * }
             */
            i = i + 1;
            done = (i >= data.length - 2);
        }
        // for each of the discovered key locations, attempt to parse a sequence of records
        for (RecordTypeEnum en : RecordTypeEnum.values()) {

        }
        for (int ix = 0; ix < keyLocations.size(); ix++) {

        }
    }


    public byte[] getRawData() {
        if (data == null) {
            return crc;
        }
        if (crc == null) {
            return data;
        }
        return ByteUtil.concat(data, crc);
    }


    protected PumpTimeStamp collectTimeStamp(byte[] data, int offset) {
        try {
            PumpTimeStamp timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, offset));
            return timestamp;
        } catch (org.joda.time.IllegalFieldValueException e) {
            return null;
        }
    }


    public boolean parsePicky(byte[] rawPage, MedtronicDeviceType model) {
        mRecordList = new ArrayList<>();
        this.model = model;
        int pageOffset = 0;

        if ((rawPage == null) || (rawPage.length == 0))
            return false;
        this.data = Arrays.copyOfRange(rawPage, 0, rawPage.length - 2);
        this.crc = Arrays.copyOfRange(rawPage, rawPage.length - 2, rawPage.length);
        byte[] expectedCrc = CRC.calculate16CCITT(this.data);
        if (DEBUG_PAGE) {
            Log.i(TAG, String.format("Data length: %d", data.length));
        }
        if (!Arrays.equals(crc, expectedCrc)) {
            Log.w(
                TAG,
                String.format("CRC does not match expected value. Expected: %s Was: %s",
                    HexDump.toHexString(expectedCrc), HexDump.toHexString(crc)));
        } else {
            if (DEBUG_PAGE) {
                Log.i(TAG, "CRC OK");
            }
        }

        Record record = null;
        while (pageOffset < data.length) {
            if (data[pageOffset] == 0) {
                if (record != null) {
                    Log.i(TAG, String.format(
                        "End of page or Previous parse fail: prev opcode 0x%02x, curr offset %d, %d bytes remaining",
                        record.getRecordOp(), pageOffset, data.length - pageOffset + 1));
                    break;
                } else {
                    Log.i(TAG, "WTF?");
                }
            }
            try {
                record = attemptParseRecord(data, pageOffset);
            } catch (org.joda.time.IllegalFieldValueException e) {
                record = null;
            }
            if (record == null) {
                Log.i(TAG, "PARSE FAIL");
                pageOffset++;
            } else {
                mRecordList.add(record);
                pageOffset += record.getLength();
            }
        }
        ArrayList<Record> pickyRecords = new ArrayList<>();
        pickyRecords.addAll(mRecordList);
        parseByDates(rawPage, model);
        for (Record r : mRecordList) {
            for (Record r2 : pickyRecords) {
                if (r.getFoundAtOffset() == r2.getFoundAtOffset()) {
                    Log.v(TAG, "Found matching record at offset " + r.getFoundAtOffset());
                }
            }
        }
        return true;
    }


    public boolean parseByDates(byte[] rawPage, MedtronicDeviceType model) {
        mRecordList = new ArrayList<>();
        if (rawPage.length != 1024) {
            Log.e(TAG, "Unexpected page size. Expected: 1024 Was: " + rawPage.length);
            // return false;
        }
        Page.model = model;
        if (DEBUG_PAGE) {
            Log.i(TAG, "Parsing page");
        }

        if (rawPage.length < 4) {
            Log.e(TAG, "Page too short, need at least 4 bytes");
            return false;
        }

        this.data = Arrays.copyOfRange(rawPage, 0, rawPage.length - 2);
        this.crc = Arrays.copyOfRange(rawPage, rawPage.length - 2, rawPage.length);
        byte[] expectedCrc = CRC.calculate16CCITT(this.data);
        if (DEBUG_PAGE) {
            Log.i(TAG, String.format("Data length: %d", data.length));
        }
        if (!Arrays.equals(crc, expectedCrc)) {
            Log.w(
                TAG,
                String.format("CRC does not match expected value. Expected: %s Was: %s",
                    HexDump.toHexString(expectedCrc), HexDump.toHexString(crc)));
        } else {
            if (DEBUG_PAGE) {
                Log.i(TAG, "CRC OK");
            }
        }

        int pageOffset = 0;
        PumpTimeStamp lastPumpTimeStamp = new PumpTimeStamp();
        while (pageOffset < this.data.length - 7) {
            PumpTimeStamp timestamp = collectTimeStamp(data, pageOffset + 2);
            if (timestamp != null) {
                String year = timestamp.toString().substring(0, 3);
                Record record;
                if ("201".equals(year)) {
                    // maybe found a record.
                    try {
                        record = attemptParseRecord(data, pageOffset);
                    } catch (org.joda.time.IllegalFieldValueException e) {
                        record = null;
                    }
                    if (record != null) {
                        if (timestamp.getLocalDateTime().compareTo(lastPumpTimeStamp.getLocalDateTime()) >= 0) {
                            Log.i(TAG, "Timestamp is increasing");
                            lastPumpTimeStamp = timestamp;
                            mRecordList.add(record);
                        } else {
                            Log.e(TAG, "Timestamp is decreasing");
                        }
                    }
                }
            }
            pageOffset++;
        }

        return true;
    }


    public boolean parseFrom(byte[] rawPage, MedtronicDeviceType model) {
        mRecordList = new ArrayList<>(); // wipe old contents each time when parsing.
        if (rawPage.length != 1024) {
            Log.e(TAG, "Unexpected page size. Expected: 1024 Was: " + rawPage.length);
            // return false;
        }
        this.model = model;
        if (DEBUG_PAGE) {
            Log.i(TAG, "Parsing page");
        }

        if (rawPage.length < 4) {
            Log.e(TAG, "Page too short, need at least 4 bytes");
            return false;
        }

        this.data = Arrays.copyOfRange(rawPage, 0, rawPage.length - 2);
        this.crc = Arrays.copyOfRange(rawPage, rawPage.length - 2, rawPage.length);
        byte[] expectedCrc = CRC.calculate16CCITT(this.data);
        if (DEBUG_PAGE) {
            Log.i(TAG, String.format("Data length: %d", data.length));
        }
        if (!Arrays.equals(crc, expectedCrc)) {
            Log.w(
                TAG,
                String.format("CRC does not match expected value. Expected: %s Was: %s",
                    HexDump.toHexString(expectedCrc), HexDump.toHexString(crc)));
        } else {
            if (DEBUG_PAGE) {
                Log.i(TAG, "CRC OK");
            }
        }

        int dataIndex = 0;
        boolean done = false;
        Record previousRecord = null;
        while (!done) {
            Record record = null;
            if (data[dataIndex] != 0) {
                // If the data byte is zero, assume that means end of page
                try {
                    record = attemptParseRecord(data, dataIndex);
                } catch (org.joda.time.IllegalFieldValueException e) {
                    record = null;
                }
            } else {
                Log.v(TAG, "Zero opcode encountered -- end of page. " + (rawPage.length - dataIndex)
                    + " bytes remaining.");

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Possible parsing problem: ");
                stringBuilder.append("Previous record: " + previousRecord);
                stringBuilder.append("  Content of previous record: "
                    + HexDump.toHexStringDisplayable(previousRecord.getRawbytes()));

                int remainingData = rawPage.length - dataIndex;
                byte[] tmpData = new byte[remainingData + 10];
                System.arraycopy(data, dataIndex, tmpData, 0, remainingData - 10);

                stringBuilder.append("  Remaining data: " + HexDump.toHexStringDisplayable(tmpData));

                Log.v(TAG, stringBuilder.toString());

                break;
            }

            if (record != null) {
                if (record instanceof IgnoredHistoryEntry) {
                    IgnoredHistoryEntry he = (IgnoredHistoryEntry)record;
                    Log.v(TAG, "parseFrom: found event " + he.getShortTypeName() + " length=" + record.getLength()
                        + " offset=" + record.getFoundAtOffset() + " -- IGNORING");
                } else {
                    Log.v(TAG,
                        "parseFrom: found event " + record.getClass().getSimpleName() + " length=" + record.getLength()
                            + " offset=" + record.getFoundAtOffset());
                    mRecordList.add(record);
                }

                dataIndex += record.getLength();

            } else {

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Possible parsing problem: ");
                stringBuilder.append("Previous record: " + previousRecord);
                stringBuilder.append("  Content of previous record: "
                    + HexDump.toHexStringDisplayable(previousRecord.getRawbytes()));

                int remainingData = data.length - dataIndex;
                byte[] tmpData = Arrays.copyOfRange(data, dataIndex, 1022);

                // new byte[remainingData];
                // System.arraycopy(data, dataIndex, tmpData, 0, remainingData - 2);

                stringBuilder.append("  Remaining data: " + HexDump.toHexStringDisplayable(tmpData));

                Log.e(TAG,
                    String.format("parseFrom: Failed to parse opcode 0x%02x, offset=%d", data[dataIndex], dataIndex));
                done = true;
            }
            if (dataIndex >= data.length - 2) {
                done = true;
            }

            previousRecord = record;
        }

        if (DEBUG_PAGE) {
            Log.i(TAG, String.format("Number of records: %d", mRecordList.size()));
            int index = 1;
            for (Record r : mRecordList) {
                Log.v(TAG, String.format("Record #%d: %s", index, r.getShortTypeName()));
                index += 1;
            }
        }
        return true;
    }


    /*
     * 
     * For IPC serialization
     */

    /*
     * private byte[] crc;
     * private byte[] data;
     * protected PumpModel model;
     * public List<Record> mRecordList;
     */

    public Bundle pack() {
        Bundle bundle = new Bundle();
        bundle.putByteArray("crc", crc);
        bundle.putByteArray("data", data);
        bundle.putString("model", model.name());
        ArrayList<Bundle> records = new ArrayList<>();
        for (int i = 0; i < mRecordList.size(); i++) {
            try {
                records.add(mRecordList.get(i).dictionaryRepresentation());
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        bundle.putParcelableArrayList("mRecordList", records);
        return bundle;
    }


    public void unpack(Bundle in) {
        crc = in.getByteArray("crc");
        data = in.getByteArray("data");
        model = MedtronicDeviceType.valueOf(in.getString("model"));
        ArrayList<Bundle> records = in.getParcelableArrayList("mRecordList");
        mRecordList = new ArrayList<>();
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                Record r = RecordTypeEnum.getRecordClassInstance(records.get(i), model);
                r.readFromBundle(records.get(i));
                mRecordList.add(r);
            }
        }
    }

}
