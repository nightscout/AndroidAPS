package info.nightscout.androidaps.plugins.ProfileNS;

import android.content.Intent;
import android.support.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.ProfileNS.events.EventNSProfileUpdateGUI;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class NSProfilePlugin extends PluginBase implements ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(NSProfilePlugin.class);

    private static NSProfilePlugin nsProfilePlugin;

    public static NSProfilePlugin getPlugin() {
        if (nsProfilePlugin == null)
            nsProfilePlugin = new NSProfilePlugin();
        return nsProfilePlugin;
    }

    private ProfileStore profile = null;

    private NSProfilePlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PROFILE)
                .fragmentClass(NSProfileFragment.class.getName())
                .pluginName(R.string.profileviewer)
                .shortName(R.string.profileviewer_shortname)
                .alwaysEnabled(Config.NSCLIENT)
                .alwayVisible(Config.NSCLIENT)
                .showInList(!Config.NSCLIENT)
        );
        loadNSProfile();
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void storeNewProfile(ProfileStore newProfile) {
        profile = new ProfileStore(newProfile.getData());
        storeNSProfile();
        MainApp.bus().post(new EventNSProfileUpdateGUI());
    }

    private void storeNSProfile() {
        SP.putString("profile", profile.getData().toString());
        if (Config.logPrefsChange)
            log.debug("Storing profile");
    }

    private void loadNSProfile() {
        if (Config.logPrefsChange)
            log.debug("Loading stored profile");
        String profileString = SP.getString("profile", null);
        if (profileString != null) {
            if (Config.logPrefsChange) {
                log.debug("Loaded profile: " + profileString);
                try {
                    profile = new ProfileStore(new JSONObject(profileString));
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                    profile = null;
                }
            }
        } else {
            if (Config.logPrefsChange) {
                log.debug("Stored profile not found");
                // force restart of nsclient to fetch profile
                Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
            }
        }
    }

    @Nullable
    @Override
    public ProfileStore getProfile() {
        return profile;
    }

    @Override
    public String getUnits() {
        return profile != null ? profile.getUnits() : Constants.MGDL;
    }

    @Override
    public String getProfileName() {
        return profile.getDefaultProfileName();
    }
}
