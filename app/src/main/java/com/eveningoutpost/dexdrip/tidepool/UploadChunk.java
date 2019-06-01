package com.eveningoutpost.dexdrip.tidepool;


import android.util.Log;

import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.UtilityModels.PersistentStore;
import com.eveningoutpost.dexdrip.utils.LogSlider;
import com.eveningoutpost.dexdrip.utils.NamedSliderProcessor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

import static com.eveningoutpost.dexdrip.Models.JoH.dateTimeText;

/**
 * jamorham
 * <p>
 * This class gets the next time slice of all data to upload
 */

public class UploadChunk implements NamedSliderProcessor {

    private static final String TAG = "TidepoolUploadChunk";
    private static final String LAST_UPLOAD_END_PREF = "tidepool-last-end";

    private static final long MAX_UPLOAD_SIZE = T.days(7).msecs(); // don't change this
    private static final long DEFAULT_WINDOW_OFFSET = T.mins(15).msecs();
    private static final long MAX_LATENCY_THRESHOLD_MINUTES = 1440; // minutes per day


    public static String getNext(final Session session) {
        session.start = getLastEnd();
        session.end = maxWindow(session.start);

        final String result = get(session.start, session.end);
        if (result != null && result.length() < 3) {
            Log.d(TAG, "No records in this time period, setting start to best end time");
            setLastEnd(Math.max(session.end, getOldestRecordTimeStamp()));
        }
        return result;
    }

    public static String get(final long start, final long end) {

        Log.e(TAG, "Syncing data between: " + dateTimeText(start) + " -> " + dateTimeText(end));
        if (end <= start) {
            Log.e(TAG, "End is <= start: " + dateTimeText(start) + " " + dateTimeText(end));
            return null;
        }
        if (end - start > MAX_UPLOAD_SIZE) {
            Log.e(TAG, "More than max range - rejecting");
            return null;
        }

        final List<BaseElement> records = new LinkedList<>();

        records.addAll(getTreatments(start, end));
        records.addAll(getBloodTests(start, end));
        records.addAll(getBasals(start, end));
        records.addAll(getBgReadings(start, end));

        return JoH.defaultGsonInstance().toJson(records);
    }

    private static long getWindowSizePreference() {
        try {
            long value = (long) getLatencySliderValue(SP.getInt(R.string.key_tidepool_window_latency, 0));
            return Math.max(T.mins(value).msecs(), DEFAULT_WINDOW_OFFSET);
        } catch (Exception e) {
            Log.e(TAG, "Reverting to default of 15 minutes due to Window Size exception: " + e);
            return DEFAULT_WINDOW_OFFSET; // default
        }
    }

    private static long maxWindow(final long last_end) {
        //Log.d(TAG, "Max window is: " + getWindowSizePreference());
        return Math.min(last_end + MAX_UPLOAD_SIZE, DateUtil.now() - getWindowSizePreference());
    }

    public static long getLastEnd() {
        long result = PersistentStore.getLong(LAST_UPLOAD_END_PREF);
        return Math.max(result, DateUtil.now() - T.months(2).msecs());
    }

    public static void setLastEnd(final long when) {
        if (when > getLastEnd()) {
            PersistentStore.setLong(LAST_UPLOAD_END_PREF, when);
            Log.d(TAG, "Updating last end to: " + dateTimeText(when));
        } else {
            Log.e(TAG, "Cannot set last end to: " + dateTimeText(when) + " vs " + dateTimeText(getLastEnd()));
        }
    }

    static List<BaseElement> getTreatments(final long start, final long end) {
        List<BaseElement> result = new LinkedList<>();
        final List<Treatment> treatments = TreatmentsPlugin.getPlugin().getService().getTreatmentDataFromTime(start, end, true);
        for (Treatment treatment : treatments) {
            if (treatment.carbs > 0) {
                result.add(EWizard.fromTreatment(treatment));
            } else if (treatment.insulin > 0) {
                result.add(EBolus.fromTreatment(treatment));
            } else {
                // note only TODO
            }
        }
        return result;
    }


    // numeric limits must match max time windows

    static long getOldestRecordTimeStamp() {
        // TODO we could make sure we include records older than the first bg record for completeness

        final long start = 0;
        final long end = DateUtil.now();

        final List<BgReading> bgReadingList = MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, false);
        if (bgReadingList != null && bgReadingList.size() > 0) {
            return bgReadingList.get(0).date;
        }
        return -1;
    }

    static List<EBloodGlucose> getBloodTests(final long start, final long end) {
        return new ArrayList<>();
//        return EBloodGlucose.fromBloodTests(BloodTest.latestForGraph(1800, start, end));
    }

    static List<ESensorGlucose> getBgReadings(final long start, final long end) {
        return ESensorGlucose.fromBgReadings(MainApp.getDbHelper().getBgreadingsDataFromTime(start, end, true));
    }

    static List<EBasal> getBasals(final long start, final long end) {
        final List<EBasal> basals = new LinkedList<>();
        final List<TemporaryBasal> aplist = MainApp.getDbHelper().getTemporaryBasalsDataFromTime(start, end, true);
        EBasal current = null;
        for (TemporaryBasal temporaryBasal : aplist) {
            final double this_rate = temporaryBasal.tempBasalConvertedToAbsolute(temporaryBasal.date, ProfileFunctions.getInstance().getProfile(temporaryBasal.date));

            if (current != null) {
                if (this_rate != current.rate) {
                    current.duration = temporaryBasal.date - current.timestamp;
                    Log.d(TAG, "Adding current: " + current.toS());
                    if (current.isValid()) {
                        basals.add(current);
                    } else {
                        Log.e(TAG, "Current basal is invalid: " + current.toS());
                    }
                    current = null;
                } else {
                    Log.d(TAG, "Same rate as previous basal record: " + current.rate + " " + temporaryBasal.toStringFull());
                }
            }
            if (current == null) {
                current = new EBasal(this_rate, temporaryBasal.date, 0, UUID.nameUUIDFromBytes(("tidepool-basal" + temporaryBasal.date).getBytes()).toString()); // start duration is 0
            }
        }
        return basals;

    }

    @Override
    public int interpolate(final String name, final int position) {
        switch (name) {
            case "latency":
                return getLatencySliderValue(position);
        }
        throw new RuntimeException("name not matched in interpolate");
    }

    private static int getLatencySliderValue(final int position) {
        return (int) LogSlider.calc(0, 300, 15, MAX_LATENCY_THRESHOLD_MINUTES, position);
    }
}
