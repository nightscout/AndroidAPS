package info.nightscout.androidaps.plugins.TuneProfile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import butterknife.BindView;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.SP;

/**
 * Created by Rumen Georgiev on 1/29/2018.
 */

public class TuneProfileFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TuneProfileFragment.class);
    public TuneProfileFragment() {super();}
    static public TuneProfilePlugin getPlugin() throws IOException {
        return TuneProfilePlugin.getPlugin();
    }

    Button runTuneNowButton;
    @BindView(R.id.tune_profileswitch)
            Button tuneProfileSwitch;
    TextView warningView;
    TextView resultView;
    TextView lastRunView;
    EditText tune_days;
    //TuneProfile tuneProfile = new TuneProfile();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.tuneprofile_fragment, container, false);

            warningView = (TextView) view.findViewById(R.id.tune_warning);
            resultView = (TextView) view.findViewById(R.id.tune_result);
            lastRunView = (TextView) view.findViewById(R.id.tune_lastrun);
            runTuneNowButton = (Button) view.findViewById(R.id.tune_run);
            tuneProfileSwitch = (Button) view.findViewById(R.id.tune_profileswitch);
            tune_days = (EditText) view.findViewById(R.id.tune_days);
            runTuneNowButton.setOnClickListener(this);
            tuneProfileSwitch.setVisibility(View.GONE);
            tuneProfileSwitch.setOnClickListener(this);
            updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @OnClick(R.id.nsprofile_profileswitch)
    public void onClickProfileSwitch() {
        String name = getString(R.string.tuneprofile_name);
        ProfileStore store = NSProfilePlugin.getPlugin().getProfile();
        if (store != null) {
            Profile profile = store.getSpecificProfile(name);
            if (profile != null) {
                OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
                        NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
                );
            }
        }
    }
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tune_run) {
            Date lastRun = new Date();
            int daysBack = Integer.parseInt(tune_days.getText().toString());
            if (daysBack > 0)
//            resultView.setText(TuneProfile.bgReadings(daysBack));
                try {
                    TuneProfilePlugin tuneProfile = new TuneProfilePlugin();
                    resultView.setText(tuneProfile.result(daysBack));
                    tuneProfileSwitch.setVisibility(View.VISIBLE);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            else
                resultView.setText("Set days between 1 and 10!!!");
            // lastrun in minutes ???
            warningView.setText("You already pressed RUN - NO WARNING NEEDED!");
            lastRunView.setText(""+lastRun.toLocaleString());
        } else if (id == R.id.tune_profileswitch){
            String name = getString(R.string.tuneprofile_name);
            ProfileStore profile = null;
            log.debug("ProfileSwitch pressed");

            String profileString = SP.getString("autotuneprofile", null);
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
            }

            if (profile != null) {
                final ProfileStore store = profile;
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
//                final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCH;
//                profileswitch.executeProfileSwitch = true;
//                newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
//                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
                        NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
                );
            } else
                log.debug("ProfileStore is null!");
        }

        //updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    warningView.setText("Don't run tune for more than 5 days back! It will cause app crashesh and too much data usage! Don't even try to run whithout WiFi connectivity!");
                    resultView.setText("Press run");
                }
            });
    }

}
