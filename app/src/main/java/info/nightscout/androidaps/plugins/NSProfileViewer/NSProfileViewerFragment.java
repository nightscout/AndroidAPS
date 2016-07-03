package info.nightscout.androidaps.plugins.NSProfileViewer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.client.data.NSProfile;

public class NSProfileViewerFragment extends Fragment implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(NSProfileViewerFragment.class);

    private static TextView noProfile;
    private static TextView units;
    private static TextView dia;
    private static TextView activeProfile;
    private static TextView ic;
    private static TextView isf;
    private static TextView basal;
    private static TextView target;

    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;

    NSProfile profile = null;

    public NSProfileViewerFragment () {
        super();
        loadNSProfile();
        registerBus();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.profileviewer);
    }

    @Override
    public boolean isEnabled() {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs() {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.nsprofileviewer_fragment, container, false);

        noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
        units = (TextView) layout.findViewById(R.id.profileview_units);
        dia = (TextView) layout.findViewById(R.id.profileview_dia);
        activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
        ic = (TextView) layout.findViewById(R.id.profileview_ic);
        isf = (TextView) layout.findViewById(R.id.profileview_isf);
        basal = (TextView) layout.findViewById(R.id.profileview_basal);
        target = (TextView) layout.findViewById(R.id.profileview_target);

        setContent();
        return layout;
    }

    public static NSProfileViewerFragment newInstance() {
        NSProfileViewerFragment fragment = new NSProfileViewerFragment();
        return fragment;
    }

    private void setContent() {
        if (profile == null) {
            noProfile.setVisibility(View.VISIBLE);
           return;
        } else {
            noProfile.setVisibility(View.GONE);
        }
        units.setText(profile.getUnits());
        dia.setText(formatNumber2decimalplaces.format(profile.getDia()) + " h");
        activeProfile.setText(profile.getActiveProfile());
        ic.setText(profile.getIcList());
        isf.setText(profile.getIsfList());
        basal.setText(profile.getBasalList());
        target.setText(profile.getTargetList());
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        profile = new NSProfile(ev.newNSProfile.getData(), ev.newNSProfile.getActiveProfile());
        storeNSProfile();
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setContent();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    private void storeNSProfile() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("profile", profile.getData().toString());
        editor.putString("activeProfile", profile.getActiveProfile());
        editor.commit();
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
