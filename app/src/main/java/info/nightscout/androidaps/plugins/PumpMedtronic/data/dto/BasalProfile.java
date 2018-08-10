package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/1/15.
 * <p>
 * There are three basal profiles stored on the pump. (722 only?) They are all parsed the same, the user just has 3 to
 * choose from: Standard, A, and B
 * <p>
 * The byte array seems to be 21 three byte entries long, plus a zero? If the profile is completely empty, it should
 * have one entry: [0,0,0x3F] (?) The first entry of [0,0,0] marks the end of the used entries.
 * <p>
 * Each entry is assumed to span from the specified start time to the start time of the next entry, or to midnight if
 * there are no more entries.
 * <p>
 * Individual entries are of the form [r,z,m] where r is the rate (in 0.025 U increments) z is zero (?) m is the start
 * time-of-day for the basal rate period (in 30 minute increments?)
 */
public class BasalProfile {

    protected static final int MAX_RAW_DATA_SIZE = (48 * 3) + 1;
    // private static final String TAG = "BasalProfile";
    private static final Logger LOG = LoggerFactory.getLogger(BasalProfile.class);
    private static final boolean DEBUG_BASALPROFILE = false;
    protected byte[] mRawData; // store as byte array to make transport (via parcel) easier
    List<BasalProfileEntry> listEntries;


    public BasalProfile() {
        init();
    }


    public BasalProfile(byte[] data) {
        setRawData(data);
    }


    // this asUINT8 should be combined with Record.asUINT8, and placed in a new util class.
    protected static int readUnsignedByte(byte b) {
        return (b < 0) ? b + 256 : b;
    }


    public static void testParser() {
        byte[] testData = new byte[] {
            32, 0, 0, 38, 0, 13, 44, 0, 19, 38, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        /*
         * from decocare:
         * _test_schedule = {'total': 22.50, 'schedule': [
         * { 'start': '12:00A', 'rate': 0.80 },
         * { 'start': '6:30A', 'rate': 0.95 },
         * { 'start': '9:30A', 'rate': 1.10 },
         * { 'start': '2:00P', 'rate': 0.95 },
         * ]}
         */
        BasalProfile profile = new BasalProfile();
        profile.setRawData(testData);
        List<BasalProfileEntry> entries = profile.getEntries();
        if (entries.isEmpty()) {
            LOG.error("testParser: failed");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                BasalProfileEntry e = entries.get(i);
                LOG.debug(String.format("testParser entry #%d: rate: %.2f, start %d:%d", i, e.rate,
                    e.startTime.getHourOfDay(), e.startTime.getMinuteOfHour()));
            }
        }

    }


    public void init() {
        mRawData = new byte[MAX_RAW_DATA_SIZE];
        mRawData[0] = 0;
        mRawData[1] = 0;
        mRawData[2] = 0x3f;
    }


    public boolean setRawData(byte[] data) {
        if (data == null) {
            LOG.error("setRawData: buffer is null!");
            return false;
        }
        // int len = Math.min(MAX_RAW_DATA_SIZE, data.length);
        mRawData = data;
        // System.arraycopy(data, 0, mRawData, 0, len);
        if (DEBUG_BASALPROFILE) {
            LOG.debug(String.format("setRawData: copied raw data buffer of %d bytes.", data.length));
        }
        return true;
    }


    public void dumpBasalProfile() {
        LOG.debug("Basal Profile entries:");
        List<BasalProfileEntry> entries = getEntries();
        for (int i = 0; i < entries.size(); i++) {
            BasalProfileEntry entry = entries.get(i);
            String startString = entry.startTime.toString("HH:mm");
            LOG.debug(String.format("Entry %d, rate=%.3f (0x%02X), start=%s (0x%02X)", i + 1, entry.rate,
                entry.rate_raw, startString, entry.startTime_raw));

        }
    }


    public String getBasalProfileAsString() {
        StringBuffer sb = new StringBuffer("Basal Profile entries:\n");
        List<BasalProfileEntry> entries = getEntries();
        for (int i = 0; i < entries.size(); i++) {
            BasalProfileEntry entry = entries.get(i);
            String startString = entry.startTime.toString("HH:mm");

            sb.append(String.format("Entry %d, rate=%.3f (0x%02X), start=%s (0x%02X)\n", i + 1, entry.rate,
                entry.rate_raw, startString, entry.startTime_raw));
        }

        return sb.toString();
    }


    // TODO: this function must be expanded to include changes in which profile is in use.
    // and changes to the profiles themselves.
    public BasalProfileEntry getEntryForTime(Instant when) {
        BasalProfileEntry rval = new BasalProfileEntry();
        List<BasalProfileEntry> entries = getEntries();
        if (entries.size() == 0) {
            LOG.warn(String.format("getEntryForTime(%s): table is empty",
                when.toDateTime().toLocalTime().toString("HH:mm")));
            return rval;
        }
        // Log.w(TAG,"Assuming first entry");
        rval = entries.get(0);
        if (entries.size() == 1) {
            LOG.debug("getEntryForTime: Only one entry in profile");
            return rval;
        }

        int localMillis = when.toDateTime().toLocalTime().getMillisOfDay();
        boolean done = false;
        int i = 1;
        while (!done) {
            BasalProfileEntry entry = entries.get(i);
            if (DEBUG_BASALPROFILE) {
                LOG.debug(String.format("Comparing 'now'=%s to entry 'start time'=%s", when.toDateTime().toLocalTime()
                    .toString("HH:mm"), entry.startTime.toString("HH:mm")));
            }
            if (localMillis >= entry.startTime.getMillisOfDay()) {
                rval = entry;
                if (DEBUG_BASALPROFILE)
                    LOG.debug("Accepted Entry");
            } else {
                // entry at i has later start time, keep older entry
                if (DEBUG_BASALPROFILE)
                    LOG.debug("Rejected Entry");
                done = true;
            }
            i++;
            if (i >= entries.size()) {
                done = true;
            }
        }
        if (DEBUG_BASALPROFILE) {
            LOG.debug(String.format("getEntryForTime(%s): Returning entry: rate=%.3f (%d), start=%s (%d)", when
                .toDateTime().toLocalTime().toString("HH:mm"), rval.rate, rval.rate_raw,
                rval.startTime.toString("HH:mm"), rval.startTime_raw));
        }
        return rval;
    }


    public List<BasalProfileEntry> getEntries() {
        List<BasalProfileEntry> entries = new ArrayList<>();

        if (mRawData[2] == 0x3f) {
            LOG.warn("Raw Data is empty.");
            return entries; // an empty list
        }
        int i = 0;
        boolean done = false;
        int r, st;
        while (!done) {

            r = MedtronicUtil.makeUnsignedShort(mRawData[i + 1], mRawData[i]); // readUnsignedByte(mRawData[i]);
            // What is mRawData[i+1]? Not used in decocare.
            st = readUnsignedByte(mRawData[i + 2]);
            entries.add(new BasalProfileEntry(r, st));
            i = i + 3;
            if (i >= MAX_RAW_DATA_SIZE) {
                done = true;
            } else if ((mRawData[i] == 0) && (mRawData[i + 1] == 0) && (mRawData[i + 2] == 0)) {
                done = true;
            }
        }
        return entries;
    }


    /**
     * This is used to prepare new profile
     *
     * @param entry
     */
    public void addEntry(BasalProfileEntry entry) {
        if (listEntries == null)
            listEntries = new ArrayList<>();

        listEntries.add(entry);
    }


    public byte[] generateRawData() {

        List<Byte> outData = new ArrayList<>();

        for (BasalProfileEntry profileEntry : listEntries) {

            byte[] strokes = MedtronicUtil.getBasalStrokes(profileEntry.rate, true);

            // TODO check if this is correct
            outData.add(profileEntry.rate_raw[0]);
            outData.add(profileEntry.rate_raw[1]);

            // int time = profileEntry.startTime.getHourOfDay();

            // if (profileEntry.startTime.getMinuteOfHour() == 30) {
            // time++;
            // }

            outData.add(profileEntry.startTime_raw);
        }

        this.setRawData(MedtronicUtil.createByteArray(outData));

        return this.mRawData;
    }


    // TODO extend to be done by half hour
    public Double[] getProfilesByHour() {

        List<BasalProfileEntry> entries = getEntries();

        Double[] basalByHour = new Double[24];

        for (int i = 0; i < entries.size(); i++) {
            BasalProfileEntry current = entries.get(i);

            int currentTime = (current.startTime_raw % 2 == 0) ? current.startTime_raw : current.startTime_raw - 1;

            currentTime = (currentTime * 30) / 60;

            int lastHour = 0;
            if ((i + 1) == entries.size()) {
                lastHour = 24;
            } else {
                BasalProfileEntry basalProfileEntry = entries.get(i + 1);

                int rawTime = (basalProfileEntry.startTime_raw % 2 == 0) ? basalProfileEntry.startTime_raw
                    : basalProfileEntry.startTime_raw - 1;

                lastHour = (rawTime * 30) / 60;
            }

            // System.out.println("Current time: " + currentTime + " Next Time: " + lastHour);

            for (int j = currentTime; j < lastHour; j++) {
                basalByHour[j] = current.rate;
            }
        }

        // StringBuilder sb = new StringBuilder();
        //
        // for (int i = 0; i < 24; i++) {
        // sb.append("" + i + "=" + basalByHour[i]);
        // sb.append("\n");
        // }
        //
        // System.out.println("Basal Profile: \n" + sb.toString());

        return basalByHour;
    }


    public byte[] getRawData() {
        return this.mRawData;
    }
}
