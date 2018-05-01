package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;
import android.util.Log;

import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by geoff on 6/25/16.
 */
public class TimeValueProfile {
    private static final String TAG = "TimeValueProfile";
    boolean mIsValid = false;
    LocalDateTime validAt;
    ArrayList<LocalTime> times = new ArrayList<>();
    HashMap<LocalTime,Object> values = new HashMap<>();

    public TimeValueProfile() {
        // initialize with very wrong timestamp.
        validAt = new LocalDateTime(1900,1,1,1,1);
    }

    public boolean isValid() {
        return mIsValid;
    }

    public ArrayList<LocalTime> getTimes() {
        return times;
    }

    // This function will return the "object" at any time of day by finding the correct interval.
    public Object getObjectForTime(LocalTime atTime) {
        LocalTime prevTime = null;
        double rval = -1.0;
        for (LocalTime t : times) {
            if (atTime.compareTo(t) < 0) {
                if (prevTime != null) {
                    Object o = values.get(prevTime);
                    if (o != null) {
                        return o;
                    }
                }
            }
            prevTime = t;
        }
        // cover last interval -- last time until next midnight
        if (prevTime != null) {
            Object o = values.get(prevTime);
            if (o!=null) {
                return o;
            }
        }
        return rval;
    }

    public boolean initFromServiceResult(ServiceResult serviceResult, String timeValueProfileName) {
        if (serviceResult == null) {
            return false;
        }
        Bundle resultMap = serviceResult.getMap();
        if (resultMap == null) {
             return false;
        }
        Bundle profile = resultMap.getBundle(timeValueProfileName);
        if (profile == null) {
            return false;
        }
        mIsValid = true;

        String validDate = profile.getString("ValidDate");
        if (validDate != null) {
            DateTimeFormatter fmt = DateTimeFormat.forPattern("YYYY-MM-ddTHH:mm:ss");
            try {
                fmt.parseLocalDateTime(validDate);
            } catch (IllegalArgumentException e) {
                // invalid format
                Log.e(TAG,"initFromServiceResult("+this.getClass().getSimpleName()+"): Failed to parse date from '"+validDate+"'");
                mIsValid = false;
            }
        } else {
            mIsValid = false;
        }

        int[] profileTimes = profile.getIntArray("times");
        float[] rates = profile.getFloatArray("rates");
        if ((profileTimes != null) && (rates!=null) && (profileTimes.length > 0) && (rates.length > 0) && (profileTimes.length == rates.length)) {
            // first value must be zero (midnight)
            if (profileTimes[0] != 0) {
                mIsValid = false;
                // but still try to load all of them.
            }
            LocalTime prevTime = null;
            boolean timesValid = true;
            for (int i=0; i<profileTimes.length; i++) {
                LocalTime startTime = new LocalTime(profileTimes[i]/60, profileTimes[i] % 60);
                times.add(startTime);
                values.put(startTime,new Double(rates[i]));
                if (prevTime != null) {
                    if (prevTime.compareTo(startTime) >= 0) {
                        timesValid = false;
                    }
                }
            }
            if (!timesValid) {
                Log.e(TAG,"initFromServiceResult(\"+this.getClass().getSimpleName()+\"): times must be monotonic");
                for (int j=0; j<profileTimes.length; j++) {
                    Log.e(TAG,String.format("initFromServiceResult(\"+this.getClass().getSimpleName()+\"): (%02d:%02d) %.2f",profileTimes[j]/60, profileTimes[j] % 60, rates[j]));
                }
            }
        } else {
            mIsValid = false;
        }

        return mIsValid;
    }




}
