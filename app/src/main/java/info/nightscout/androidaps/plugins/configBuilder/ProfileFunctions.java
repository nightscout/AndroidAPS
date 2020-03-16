package info.nightscout.androidaps.plugins.configBuilder;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;

public class ProfileFunctions implements ProfileFunction {
    private static Logger log = StacktraceLoggerWrapper.getLogger(L.PROFILE);

    private static ProfileFunctions profileFunctions = null;

    @Deprecated
    public static ProfileFunctions getInstance() {
        if (profileFunctions == null)
            profileFunctions = new ProfileFunctions();
        return profileFunctions;
    }

    @NotNull
    public String getProfileName() {
        return getProfileName(System.currentTimeMillis(), true, false);
    }

    @NotNull
    public String getProfileName(boolean customized) {
        return getProfileName(System.currentTimeMillis(), customized, false);
    }

    @NotNull
    public String getProfileNameWithDuration() {
        return getProfileName(System.currentTimeMillis(), true, true);
    }

    @NotNull
    public String getProfileName(long time, boolean customized, boolean showRemainingTime) {
        String profileName = MainApp.gs(R.string.noprofileselected);

        TreatmentsInterface activeTreatments = TreatmentsPlugin.getPlugin();
        ProfileInterface activeProfile = PluginStore.Companion.getInstance().getActiveProfileInterface();

        ProfileSwitch profileSwitch = activeTreatments.getProfileSwitchFromHistory(time);
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                profileName = customized ? profileSwitch.getCustomizedName() : profileSwitch.profileName;
            } else {
                ProfileStore profileStore = activeProfile.getProfile();
                if (profileStore != null) {
                    Profile profile = profileStore.getSpecificProfile(profileSwitch.profileName);
                    if (profile != null)
                        profileName = profileSwitch.profileName;
                }
            }

            if (showRemainingTime && profileSwitch.durationInMinutes != 0) {
                profileName += DateUtil.untilString(profileSwitch.originalEnd());
            }
            return profileName;
        }
        return profileName;
    }

    public boolean isProfileValid(@NotNull String from) {
        Profile profile = getProfile();
        return profile != null && profile.isValid(from);
    }

    @Nullable
    public Profile getProfile() {
        return getProfile(System.currentTimeMillis());
    }

    @NotNull
    public String getUnits() {
        return getSystemUnits();
    }

    @NotNull
    @Deprecated
    public static String getSystemUnits() {
        return SP.getString(R.string.key_units, Constants.MGDL);
    }

    @Nullable
    public Profile getProfile(long time) {
        TreatmentsInterface activeTreatments = TreatmentsPlugin.getPlugin();
        ProfileInterface activeProfile = PluginStore.Companion.getInstance().getActiveProfileInterface();

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
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "CatchedError");
            bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, BuildConfig.BUILDVERSION);
            bundle.putString(FirebaseAnalytics.Param.START_DATE, String.valueOf(time));
            bundle.putString(FirebaseAnalytics.Param.VALUE, activeTreatments.getProfileSwitchesFromHistory().toString());
            FabricPrivacy.getInstance().logCustom(bundle);
        }
        log.error("getProfile at the end: returning null");
        return null;
    }

    @NotNull
    public ProfileSwitch prepareProfileSwitch(@NotNull final ProfileStore profileStore, @NotNull final String profileName, final int duration, final int percentage, final int timeShift, long date) {
        ProfileSwitch profileSwitch = new ProfileSwitch();
        profileSwitch.date = date;
        profileSwitch.source = Source.USER;
        profileSwitch.profileName = profileName;
        profileSwitch.profileJson = profileStore.getSpecificProfile(profileName).getData().toString();
        profileSwitch.profilePlugin = PluginStore.Companion.getInstance().getActiveProfileInterface().getClass().getName();
        profileSwitch.durationInMinutes = duration;
        profileSwitch.isCPP = percentage != 100 || timeShift != 0;
        profileSwitch.timeshift = timeShift;
        profileSwitch.percentage = percentage;
        return profileSwitch;
    }

}
