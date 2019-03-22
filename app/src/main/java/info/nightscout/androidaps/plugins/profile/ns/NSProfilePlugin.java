package info.nightscout.androidaps.plugins.profile.ns;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.events.EventProfileStoreChanged;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.profile.ns.events.EventNSProfileUpdateGUI;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class NSProfilePlugin extends PluginBase implements ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(L.PROFILE);

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
                .pluginName(R.string.nsprofile)
                .shortName(R.string.profileviewer_shortname)
                .alwaysEnabled(Config.NSCLIENT)
                .alwayVisible(Config.NSCLIENT)
                .showInList(!Config.NSCLIENT)
                .description(R.string.description_profile_nightscout)
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

    public void handleNewData(Intent intent) {
        try {
            Bundle bundles = intent.getExtras();
            if (bundles == null) return;

            String activeProfile = bundles.getString("activeprofile");
            String profileString = bundles.getString("profile");
            profile = new ProfileStore(new JSONObject(profileString));
            storeNSProfile();
            if (isEnabled(PluginType.PROFILE)) {
                MainApp.bus().post(new EventProfileStoreChanged());
                MainApp.bus().post(new EventNSProfileUpdateGUI());
            }
            if (L.isEnabled(L.PROFILE))
                log.debug("Received profileStore: " + activeProfile + " " + profile);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    private void storeNSProfile() {
        SP.putString("profile", profile.getData().toString());
        if (L.isEnabled(L.PROFILE))
            log.debug("Storing profile");
    }

    private void loadNSProfile() {
        if (L.isEnabled(L.PROFILE))
            log.debug("Loading stored profile");
        String profileString = SP.getString("profile", null);
        if (profileString != null) {
            if (L.isEnabled(L.PROFILE))
                log.debug("Loaded profile: " + profileString);
            try {
                profile = new ProfileStore(new JSONObject(profileString));
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
                profile = null;
            }
        } else {
            if (L.isEnabled(L.PROFILE))
                log.debug("Stored profile not found");
            // force restart of nsclient to fetch profile
            Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
            MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
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
