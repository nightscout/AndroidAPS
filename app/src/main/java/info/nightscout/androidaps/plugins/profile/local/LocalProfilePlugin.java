package info.nightscout.androidaps.plugins.profile.local;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.events.EventProfileStoreChanged;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class LocalProfilePlugin extends PluginBase implements ProfileInterface {
    public static final String LOCAL_PROFILE = "LocalProfile";
    private static Logger log = LoggerFactory.getLogger(L.PROFILE);

    private static LocalProfilePlugin localProfilePlugin;

    public static LocalProfilePlugin getPlugin() {
        if (localProfilePlugin == null)
            localProfilePlugin = new LocalProfilePlugin();
        return localProfilePlugin;
    }

    private ProfileStore convertedProfile = null;

    private static final String DEFAULTARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]";

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    boolean edited;
    boolean mgdl;
    boolean mmol;
    Double dia;
    JSONArray ic;
    JSONArray isf;
    JSONArray basal;
    JSONArray targetLow;
    JSONArray targetHigh;

    public LocalProfilePlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PROFILE)
                .fragmentClass(LocalProfileFragment.class.getName())
                .pluginName(R.string.localprofile)
                .shortName(R.string.localprofile_shortname)
                .description(R.string.description_profile_local)
        );
        loadSettings();
    }

    public synchronized void storeSettings() {
        SP.putBoolean(LOCAL_PROFILE + "mmol", mmol);
        SP.putBoolean(LOCAL_PROFILE + "mgdl", mgdl);
        SP.putString(LOCAL_PROFILE + "dia", dia.toString());
        SP.putString(LOCAL_PROFILE + "ic", ic.toString());
        SP.putString(LOCAL_PROFILE + "isf", isf.toString());
        SP.putString(LOCAL_PROFILE + "basal", basal.toString());
        SP.putString(LOCAL_PROFILE + "targetlow", targetLow.toString());
        SP.putString(LOCAL_PROFILE + "targethigh", targetHigh.toString());

        createAndStoreConvertedProfile();
        edited = false;
        if (L.isEnabled(L.PROFILE))
            log.debug("Storing settings: " + getRawProfile().getData().toString());
        RxBus.INSTANCE.send(new EventProfileStoreChanged());
    }

    public synchronized void loadSettings() {
        if (L.isEnabled(L.PROFILE))
            log.debug("Loading stored settings");

        mgdl = SP.getBoolean(LOCAL_PROFILE + "mgdl", false);
        mmol = SP.getBoolean(LOCAL_PROFILE + "mmol", true);
        dia = SP.getDouble(LOCAL_PROFILE + "dia", Constants.defaultDIA);
        try {
            ic = new JSONArray(SP.getString(LOCAL_PROFILE + "ic", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                ic = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            isf = new JSONArray(SP.getString(LOCAL_PROFILE + "isf", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                isf = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            basal = new JSONArray(SP.getString(LOCAL_PROFILE + "basal", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                basal = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            targetLow = new JSONArray(SP.getString(LOCAL_PROFILE + "targetlow", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                targetLow = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        try {
            targetHigh = new JSONArray(SP.getString(LOCAL_PROFILE + "targethigh", DEFAULTARRAY));
        } catch (JSONException e1) {
            try {
                targetHigh = new JSONArray(DEFAULTARRAY);
            } catch (JSONException ignored) {
            }
        }
        edited = false;
        createAndStoreConvertedProfile();
    }

    /*
        {
            "_id": "576264a12771b7500d7ad184",
            "startDate": "2016-06-16T08:35:00.000Z",
            "defaultProfile": "Default",
            "store": {
                "Default": {
                    "dia": "3",
                    "carbratio": [{
                        "time": "00:00",
                        "value": "30"
                    }],
                    "carbs_hr": "20",
                    "delay": "20",
                    "sens": [{
                        "time": "00:00",
                        "value": "100"
                    }],
                    "timezone": "UTC",
                    "basal": [{
                        "time": "00:00",
                        "value": "0.1"
                    }],
                    "target_low": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "target_high": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "startDate": "1970-01-01T00:00:00.000Z",
                    "units": "mmol"
                }
            },
            "created_at": "2016-06-16T08:34:41.256Z"
        }
        */
    private void createAndStoreConvertedProfile() {
        convertedProfile = createProfileStore();
    }

    public synchronized boolean isValidEditState() {
        return createProfileStore().getDefaultProfile().isValid(MainApp.gs(R.string.localprofile), false);
    }

    @NonNull
    public ProfileStore createProfileStore() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

        try {
            json.put("defaultProfile", LOCAL_PROFILE);
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", ic);
            profile.put("sens", isf);
            profile.put("basal", basal);
            profile.put("target_low", targetLow);
            profile.put("target_high", targetHigh);
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put(LOCAL_PROFILE, profile);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return new ProfileStore(json);
    }

    @Override
    public ProfileStore getProfile() {
        if (!convertedProfile.getDefaultProfile().isValid(MainApp.gs(R.string.localprofile)))
            return null;
        return convertedProfile;
    }

    public ProfileStore getRawProfile() {
        return convertedProfile;
    }

    @Override
    public String getUnits() {
        return mgdl ? Constants.MGDL : Constants.MMOL;
    }

    @Override
    public String getProfileName() {
        return DecimalFormatter.to2Decimal(convertedProfile.getDefaultProfile().percentageBasalSum()) + "U ";
    }

}
