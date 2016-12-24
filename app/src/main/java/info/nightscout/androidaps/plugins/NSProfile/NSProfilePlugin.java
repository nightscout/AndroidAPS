package info.nightscout.androidaps.plugins.NSProfile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.NSProfile.events.EventNSProfileUpdateGUI;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 05.08.2016.
 */
public class NSProfilePlugin implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(NSProfilePlugin.class);

    @Override
    public String getFragmentClass() {
        return NSProfileFragment.class.getName();
    }

    static boolean fragmentEnabled = true;
    static boolean fragmentVisible = true;

    static NSProfile profile = null;

    public NSProfilePlugin() {
        MainApp.bus().register(this);
        loadNSProfile();

    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.profileviewer);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PROFILE && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PROFILE && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PROFILE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PROFILE) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        profile = new NSProfile(ev.newNSProfile.getData(), ev.newNSProfile.getActiveProfile());
        storeNSProfile();
        MainApp.bus().post(new EventNSProfileUpdateGUI());
    }

    private void storeNSProfile() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("profile", profile.getData().toString());
        editor.putString("activeProfile", profile.getActiveProfile());
        editor.apply();
        if (Config.logPrefsChange)
            log.debug("Storing profile");
    }

    private void loadNSProfile() {
        if (Config.logPrefsChange)
            log.debug("Loading stored profile");
        SharedPreferences store = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String activeProfile = store.getString("activeProfile", null);
        String profileString = store.getString("profile", null);
        if (profileString != null) {
            if (Config.logPrefsChange) {
                log.debug("Loaded profile: " + profileString);
                log.debug("Loaded active profile: " + activeProfile);
                try {
                    profile = new NSProfile(new JSONObject(profileString), activeProfile);
                } catch (JSONException e) {
                    e.printStackTrace();
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
    public NSProfile getProfile() {
        return profile;
    }
}
