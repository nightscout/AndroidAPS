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
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ProfileNS.events.EventNSProfileUpdateGUI;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class NSProfilePlugin implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(NSProfilePlugin.class);

    private static NSProfilePlugin nsProfilePlugin;

    public static NSProfilePlugin getPlugin() {
        if (nsProfilePlugin == null)
            nsProfilePlugin = new NSProfilePlugin();
        return nsProfilePlugin;
    }

    @Override
    public String getFragmentClass() {
        return NSProfileFragment.class.getName();
    }

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;

    private static ProfileStore profile = null;

    private NSProfilePlugin() {
        MainApp.bus().register(this);
        loadNSProfile();

    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.profileviewer);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.profileviewer_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PROFILE && (Config.NSCLIENT || Config.G5UPLOADER|| fragmentEnabled);
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PROFILE && (Config.NSCLIENT || Config.G5UPLOADER|| fragmentVisible);
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return !Config.NSCLIENT && !Config.G5UPLOADER;
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
    public int getPreferencesId() {
        return -1;
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Subscribe
    public static void storeNewProfile(ProfileStore newProfile) {
        profile = new ProfileStore(newProfile.getData());
        storeNSProfile();
        MainApp.bus().post(new EventNSProfileUpdateGUI());
        ConfigBuilderPlugin.getCommandQueue().setProfile(MainApp.getConfigBuilder().getProfile(), new Callback() {
            @Override
            public void run() {
                if (result.enacted) {
                    SmsCommunicatorPlugin smsCommunicatorPlugin = MainApp.getSpecificPlugin(SmsCommunicatorPlugin.class);
                    if (smsCommunicatorPlugin != null && smsCommunicatorPlugin.isEnabled(PluginBase.GENERAL)) {
                        smsCommunicatorPlugin.sendNotificationToAllNumbers(MainApp.sResources.getString(R.string.profile_set_ok));
                    }
                }
            }
        });
    }

    private static void storeNSProfile() {
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
