package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;

import javax.inject.Inject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.StaticInjector;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

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
    public int duration;            // msec converted in minutes
    public boolean isFakeExtended;
    public String enteredBy;
    public int percentRate;
    public boolean isAbsolute;
    public ExtendedBolus extendedBolus;
    private String origin;
    @Inject ResourceHelper resourceHelper;
    //CarePortalEvents
    public CareportalEvent careportalEvent;
    public String json;


    public NsTreatment(Treatment t) {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
        treatment = t;
        _id=t._id;
        date=t.date;
        carbs=t.carbs;
        insulin=t.insulin;
        isSMB=t.isSMB;
        isValid=t.isValid;
        mealBolus=t.mealBolus;

        if(insulin > 0 && carbs > 0)
            eventType = CareportalEvent.BOLUSWIZARD;
        else if (carbs > 0)
            eventType = CareportalEvent.CARBCORRECTION;
        else
            eventType = CareportalEvent.CORRECTIONBOLUS;
        created_at = DateUtil.toISOString(t.date);
    }

    public NsTreatment (CareportalEvent t) {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
        careportalEvent = t;
        _id=t._id;
        date=t.date;
        created_at = DateUtil.toISOString(t.date);
        eventType = t.eventType;
        duration= Math.round(t.getDuration() / 60f / 1000);
        isValid=t.isValid;
        json=t.json;
    }


    public NsTreatment (TemporaryBasal t) {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
        temporaryBasal=t;
        _NsTreatment(t);
    }
    public NsTreatment(ExtendedBolus t) {
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
        extendedBolus = t;
        _NsTreatment(new TemporaryBasal(t));
    }

    private void _NsTreatment (TemporaryBasal t) {
        _id=t._id;
        date=t.date;
        absoluteRate= Round.roundTo(t.absoluteRate,0.001);
        isValid=t.isValid;
        isEndingEvent=t.isEndingEvent();
        eventType = CareportalEvent.TEMPBASAL;
        enteredBy = "openaps://" + resourceHelper.gs(R.string.app_name);
        duration = t.getRealDuration();
        percentRate = t.percentRate;
        isFakeExtended = t.isFakeExtended;
        created_at = DateUtil.toISOString(t.date);
        isAbsolute = t.isAbsolute;
    }

    //todo: See NSUpload to be as close as possible for each event type
    public JSONObject toJson() {
        JSONObject cPjson = new JSONObject();
        try {
            cPjson.put("_id", _id);
            cPjson.put("eventType",eventType);
            cPjson.put("date",date);
            cPjson.put("created_at",created_at);
            cPjson.put("insulin",insulin > 0 ? insulin : JSONObject.NULL);
            cPjson.put("carbs",carbs > 0 ? carbs : JSONObject.NULL );
            if(eventType==CareportalEvent.TEMPBASAL) {
                if (!isEndingEvent) {
                    cPjson.put("duration", duration);
                    cPjson.put("absolute", absoluteRate);
                    cPjson.put("rate", absoluteRate);
                    // cPjson.put("percent", percentRate - 100);
                    cPjson.put("isFakeExtended", isFakeExtended);
                }
                cPjson.put("enteredBy", enteredBy);
                //cPjson.put("isEnding", isEndingEvent);

            } else {
                cPjson.put("isSMB", isSMB);
                cPjson.put("isMealBolus", mealBolus);
            }

        } catch (JSONException e) {}
        return cPjson;
    }

}
