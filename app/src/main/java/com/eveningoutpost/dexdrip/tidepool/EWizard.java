package com.eveningoutpost.dexdrip.tidepool;

import com.google.gson.annotations.Expose;

import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.treatments.Treatment;

// jamorham

public class EWizard extends BaseElement {

    @Expose
    public String units = "mg/dL";
    @Expose
    public double carbInput;
    @Expose
    public double insulinCarbRatio;
    @Expose
    public EBolus bolus;

    EWizard() {
        type = "wizard";
    }

    public static EWizard fromTreatment(final Treatment treatment) {
        final EWizard result = (EWizard)new EWizard().populate(treatment.date, "uuid-AAPS");
        result.carbInput = treatment.carbs;
        result.insulinCarbRatio = ProfileFunctions.getInstance().getProfile(treatment.date).getIc();
        if (treatment.insulin > 0) {
            result.bolus = new EBolus(treatment.insulin, treatment.insulin, treatment.date, "uuid-AAPS");
        } else {
            result.bolus = new EBolus(0.0001,0.0001, treatment.date, "uuid-AAPS"); // fake insulin record
        }
        return result;
        }

}
