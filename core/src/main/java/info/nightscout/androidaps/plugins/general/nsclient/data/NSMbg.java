package info.nightscout.androidaps.plugins.general.nsclient.data;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;

public class NSMbg {
    @Inject public AAPSLogger aapsLogger;

    public long date;
    public double mbg;
    public String json;

    public NSMbg(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public NSMbg(HasAndroidInjector injector, JSONObject json) {
        this(injector);
        try {
            date = json.getLong("mills");
            mbg = json.getDouble("mgdl");
            this.json = json.toString();
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
            aapsLogger.error("Data: " + json.toString());
        }
    }

    public String id() {
        try {
            return new JSONObject(json).getString("_id");
        } catch (JSONException e) {
            return null;
        }
    }
}
