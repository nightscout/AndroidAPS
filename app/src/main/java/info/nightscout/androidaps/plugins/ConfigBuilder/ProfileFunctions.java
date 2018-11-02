package info.nightscout.androidaps.plugins.ConfigBuilder;

import android.content.Intent;
import android.support.annotation.Nullable;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.FabricPrivacy;

public class ProfileFunctions {
    private static Logger log = LoggerFactory.getLogger(L.PROFILE);

    private static ProfileFunctions profileFunctions = null;

    public static ProfileFunctions getInstance() {
        if (profileFunctions == null)
            profileFunctions = new ProfileFunctions();
        return profileFunctions;
    }

    static {
        ProfileFunctions.getInstance(); // register to bus at start
    }

    ProfileFunctions() {
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onProfileSwitch(EventProfileSwitchChange ignored) {
        if (L.isEnabled(L.PROFILE))
            log.debug("onProfileSwitch");
        ConfigBuilderPlugin.getPlugin().getCommandQueue().setProfile(getProfile(), new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", result.comment);
                    i.putExtra("title", MainApp.gs(R.string.failedupdatebasalprofile));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainApp.instance().startActivity(i);
                }
                MainApp.bus().post(new EventNewBasalProfile());
            }
        });
    }

    public String getProfileName() {
        return getProfileName(System.currentTimeMillis());
    }

    public String getProfileName(boolean customized) {
        return getProfileName(System.currentTimeMillis(), customized);
    }

    public String getProfileName(long time) {
        return getProfileName(time, true);
    }

    public String getProfileName(long time, boolean customized) {
        TreatmentsInterface activeTreatments = TreatmentsPlugin.getPlugin();
        ProfileInterface activeProfile = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface();

        ProfileSwitch profileSwitch = activeTreatments.getProfileSwitchFromHistory(time);
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                return customized ? profileSwitch.getCustomizedName() : profileSwitch.profileName;
            } else {
                ProfileStore profileStore = activeProfile.getProfile();
                if (profileStore != null) {
                    Profile profile = profileStore.getSpecificProfile(profileSwitch.profileName);
                    if (profile != null)
                        return profileSwitch.profileName;
                }
            }
        }
        return MainApp.gs(R.string.noprofileselected);
    }

    public boolean isProfileValid(String from) {
        return getProfile() != null && getProfile().isValid(from);
    }

    @Nullable
    public Profile getProfile() {
        return getProfile(System.currentTimeMillis());
    }

    public String getProfileUnits() {
        Profile profile = getProfile();
        return profile != null ? profile.getUnits() : Constants.MGDL;
    }

    @Nullable
    public Profile getProfile(long time) {
        TreatmentsInterface activeTreatments = TreatmentsPlugin.getPlugin();
        ProfileInterface activeProfile = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface();

        //log.debug("Profile for: " + new Date(time).toLocaleString() + " : " + getProfileName(time));
        ProfileSwitch profileSwitch = activeTreatments.getProfileSwitchFromHistory(time);
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                return profileSwitch.getProfileObject();
            } else if (activeProfile.getProfile() != null) {
                Profile profile = activeProfile.getProfile().getSpecificProfile(profileSwitch.profileName);
                if (profile != null)
                    return profile;
            }
        }
        if (activeTreatments.getProfileSwitchesFromHistory().size() > 0) {
            FabricPrivacy.getInstance().logCustom(new CustomEvent("CatchedError")
                    .putCustomAttribute("buildversion", BuildConfig.BUILDVERSION)
                    .putCustomAttribute("version", BuildConfig.VERSION)
                    .putCustomAttribute("time", time)
                    .putCustomAttribute("getProfileSwitchesFromHistory", activeTreatments.getProfileSwitchesFromHistory().toString())
            );
        }
        log.error("getProfile at the end: returning null");
        return null;
    }

}
