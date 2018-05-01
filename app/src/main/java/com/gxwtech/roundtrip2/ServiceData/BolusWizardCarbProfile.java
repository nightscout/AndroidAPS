package com.gxwtech.roundtrip2.ServiceData;

import org.joda.time.LocalTime;

/**
 * Created by geoff on 6/25/16.
 */
public class BolusWizardCarbProfile extends TimeValueProfile {
    public BolusWizardCarbProfile() {
    }

    public double getCarbRatioForTime(LocalTime atTime) {
        Double rval = -999999999.0; // clearly invalid
        Object o = getObjectForTime(atTime);
        if (o != null) {
            try {
                rval = (Double) o;
            } catch (ClassCastException e) {
            }
        }
        return rval;
    }

    public boolean initFromServiceResult(ServiceResult serviceResult) {
        return initFromServiceResult(serviceResult, "BolusWizardCarbProfile");
    }
}
