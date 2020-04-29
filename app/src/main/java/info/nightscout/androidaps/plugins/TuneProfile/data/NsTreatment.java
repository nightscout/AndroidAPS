package info.nightscout.androidaps.plugins.TuneProfile.data;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;

public class NsTreatment {
    //Common properties
    public String _id;
    public long date;
    public boolean isValid;
    public String eventType;
    public String created_at;

    // treatment properties
    public Treatment treatment;
    public Double insulin=0d;
    public Double carbs=0d;
    public boolean isSMB;
    public boolean mealBolus;

    //TemporayBasal or ExtendedBolus properties
    public TemporaryBasal temporaryBasal;
    public Double absoluteRate;
    public boolean isEndingEvent;
    public int duration;
    public boolean isFakeExtended;
    public String enteredBy;
    public int percentRate;
    public boolean isAbsolute;
    public ExtendedBolus extendedBolus;


    public NsTreatment(Treatment t) {
        treatment = t;
        _id=t._id;
        date=t.date;
        carbs=t.carbs;
        insulin=t.insulin;
        isSMB=t.isSMB;
        isValid=t.isValid;
        mealBolus=t.mealBolus;
        if(insulin > 0 && carbs > 0)
            eventType = "Bolus Wizard";
        else if (carbs > 0)
            eventType = "Carb Correction";
        else
            eventType = "Correction Bolus";
        created_at = DateUtil.toISOString(t.date);
    }
    public NsTreatment (TemporaryBasal t) {
        temporaryBasal=t;
        _NsTreatment(t);
    }
    public NsTreatment(ExtendedBolus t) {
        extendedBolus = t;
        _NsTreatment(new TemporaryBasal(t));
    }

    private void _NsTreatment (TemporaryBasal t) {
        _id=t._id;
        date=t.date;
        absoluteRate=t.absoluteRate;
        isValid=t.isValid;
        isEndingEvent=t.isEndingEvent();
        eventType = "Temp Basal";
        enteredBy = "openaps://AndroidAPS";
        duration = t.getRealDuration();
        percentRate = t.percentRate;
        isFakeExtended = t.isFakeExtended;
        created_at = DateUtil.toISOString(t.date);
        isAbsolute = t.isAbsolute;
    }

    public JSONObject toJson() {
        JSONObject cPjson = new JSONObject();
        try {
            cPjson.put("_id", _id);
            cPjson.put("eventType",eventType);
            cPjson.put("date",date);
            cPjson.put("created_at",created_at);
            cPjson.put("insulin",insulin > 0 ? insulin : JSONObject.NULL);
            cPjson.put("carbs",carbs > 0 ? carbs : JSONObject.NULL );
            if (!isEndingEvent)
                cPjson.put("duration", duration);
            cPjson.put("absolute", absoluteRate);
            cPjson.put("rate", absoluteRate);
            cPjson.put("percent", percentRate);
            cPjson.put("enteredBy",enteredBy);
            cPjson.put("isSMB",isSMB);
            cPjson.put("isMealBolus",mealBolus);
            cPjson.put("isEnding", isEndingEvent);
            cPjson.put("isFakeExtended", isFakeExtended);
        } catch (JSONException e) {}
        return cPjson;
    }

}
