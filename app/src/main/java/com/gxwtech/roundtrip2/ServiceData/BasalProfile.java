package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;
import org.joda.time.LocalTime;

/**
 * Created by geoff on 6/25/16.
 */
public class BasalProfile extends TimeValueProfile {
    String profileType = "unknown";
    public BasalProfile() {
    }

    public double getRateForTime(LocalTime atTime) {
        Double rval = -999999999.0; // clearly invalid
        Object o = getObjectForTime(atTime);
        if (o != null) {
            try {
                rval = (Double)o;
            } catch (ClassCastException e) {
            }
        }
        return rval;
    }

    public boolean initFromServiceResult(ServiceResult serviceResult) {
        boolean initValid = initFromServiceResult(serviceResult,"BasalProfile");
        // now get our specific values from the BasalProfile
        Bundle resultMap = serviceResult.getMap();
        if (resultMap != null) {
            Bundle profile = resultMap.getBundle("BasalProfile");
            if (profile == null) {
                return false;
            }
            String which = profile.getString("ProfileType");
            if (which != null) {
                this.profileType = which;
            } else {
                mIsValid = false;
            }
        } else {
            mIsValid = false;
        }
        mIsValid = initValid;
        return mIsValid;
    }
}
