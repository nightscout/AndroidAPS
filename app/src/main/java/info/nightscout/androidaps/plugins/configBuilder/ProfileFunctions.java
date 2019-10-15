package info.nightscout.androidaps.plugins.configBuilder;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class ProfileFunctions {
    private static Logger log = LoggerFactory.getLogger(L.PROFILE);
    private CompositeDisposable disposable = new CompositeDisposable();

    private static ProfileFunctions profileFunctions = null;

    public static ProfileFunctions getInstance() {
        if (profileFunctions == null)
            profileFunctions = new ProfileFunctions();
        return profileFunctions;
    }

    static {
        ProfileFunctions.getInstance(); // register to bus at start
    }

    private ProfileFunctions() {
        disposable.add(RxBus.INSTANCE
                .toObservable(EventProfileNeedsUpdate.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
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
                            if (result.enacted)
                                RxBus.INSTANCE.send(new EventNewBasalProfile());
                        }
                    });
                }, FabricPrivacy::logException)
        );
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
        Profile profile = getProfile();
        return profile != null && profile.isValid(from);
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

    public static ProfileSwitch prepareProfileSwitch(final ProfileStore profileStore, final String profileName, final int duration, final int percentage, final int timeshift, long date) {
        ProfileSwitch profileSwitch = new ProfileSwitch();
        profileSwitch.date = date;
        profileSwitch.source = Source.USER;
        profileSwitch.profileName = profileName;
        profileSwitch.profileJson = profileStore.getSpecificProfile(profileName).getData().toString();
        profileSwitch.profilePlugin = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getClass().getName();
        profileSwitch.durationInMinutes = duration;
        profileSwitch.isCPP = percentage != 100 || timeshift != 0;
        profileSwitch.timeshift = timeshift;
        profileSwitch.percentage = percentage;
        return profileSwitch;
    }

    public static void doProfileSwitch(final ProfileStore profileStore, final String profileName, final int duration, final int percentage, final int timeshift) {
        ProfileSwitch profileSwitch = prepareProfileSwitch(profileStore, profileName, duration, percentage, timeshift, System.currentTimeMillis());
        TreatmentsPlugin.getPlugin().addToHistoryProfileSwitch(profileSwitch);
        if (percentage == 90 && duration == 10)
            SP.putBoolean(R.string.key_objectiveuseprofileswitch, true);
    }

    public static void doProfileSwitch(final int duration, final int percentage, final int timeshift) {
        ProfileSwitch profileSwitch = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(System.currentTimeMillis());
        if (profileSwitch != null) {
            profileSwitch = new ProfileSwitch();
            profileSwitch.date = System.currentTimeMillis();
            profileSwitch.source = Source.USER;
            profileSwitch.profileName = getInstance().getProfileName(System.currentTimeMillis(), false);
            profileSwitch.profileJson = getInstance().getProfile().getData().toString();
            profileSwitch.profilePlugin = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getClass().getName();
            profileSwitch.durationInMinutes = duration;
            profileSwitch.isCPP = percentage != 100 || timeshift != 0;
            profileSwitch.timeshift = timeshift;
            profileSwitch.percentage = percentage;
            TreatmentsPlugin.getPlugin().addToHistoryProfileSwitch(profileSwitch);
        } else {
            log.error("No profile switch existing");
        }
    }

}
