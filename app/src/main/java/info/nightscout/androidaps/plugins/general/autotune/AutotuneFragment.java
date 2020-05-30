package info.nightscout.androidaps.plugins.general.autotune;

import android.app.Activity;
import android.os.Bundle;

import dagger.android.support.DaggerFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

//2 unknown imports disabled by philoul to build AAPS
//import butterknife.BindView;
//import butterknife.OnClick;
import javax.inject.Inject;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by Rumen Georgiev on 1/29/2018.
 * Rebase with current dev by philoul on 03/02/2020
 */

/* Todo: Reset results field when
*       1   lastrun is older than Today 4 AM
*       2   Nb of day is changed
* Memorise NbDay of lastRun (inconsistencies if you leave AutotunePlugin and enter again in it (results of calculation still shown but default nb of days selected
* Add rxBus, and event management to update field results during calculation (calculation as to be in dedicated thread
* Add Copy to localPlugin button (to allow modification of tuned profile before ProfileSwitch
* Enable Direct ProfileSwitch button (and probably refactor UI)
*/
public class AutotuneFragment extends DaggerFragment implements View.OnClickListener {
    @Inject NSProfilePlugin nsProfilePlugin;
    @Inject AutotunePlugin autotunePlugin;
    @Inject SP sp;
    @Inject DateUtil dateUtil;
    @Inject ProfileFunction profileFunction;
    @Inject ResourceHelper resourceHelper;

    public AutotuneFragment() {super();}

    Button runTuneNowButton;
// disabled by philoul to build AAPS
//    @BindView(R.id.tune_profileswitch)
    Button tuneProfileSwitch;
    TextView warningView;
    TextView resultView;
    TextView lastRunView;
    EditText tune_days;
    //autotune tuneProfile = new autotune();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.autotune_fragment, container, false);

            warningView = (TextView) view.findViewById(R.id.tune_warning);
            resultView = (TextView) view.findViewById(R.id.tune_result);
            lastRunView = (TextView) view.findViewById(R.id.tune_lastrun);
            runTuneNowButton = (Button) view.findViewById(R.id.tune_run);
            tuneProfileSwitch = (Button) view.findViewById(R.id.tune_profileswitch);
            tune_days = (EditText) view.findViewById(R.id.tune_days);
            runTuneNowButton.setOnClickListener(this);
            tuneProfileSwitch.setVisibility(View.GONE);
            tuneProfileSwitch.setOnClickListener(this);

            tune_days.setText(sp.getString(R.string.key_autotune_default_tune_days,"5"));
            warningView.setText(addWarnings());
            resultView.setText(AutotunePlugin.result);
            String latRunTxt = AutotunePlugin.lastRun != null ? dateUtil.dateAndTimeString(AutotunePlugin.lastRun) : "";
            lastRunView.setText(latRunTxt);
            updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

// disabled by philoul to build AAPS
// @OnClick(R.id.nsprofile_profileswitch)
    public void onClickProfileSwitch() {
        String name = resourceHelper.gs(R.string.autotune_tunedprofile_name);
        ProfileStore store = nsProfilePlugin.getProfile();
        if (store != null) {
            Profile profile = store.getSpecificProfile(name);
            if (profile != null) {
// todo Philoul activate profile switch once AT is Ok (to update to be compatible with local profiles)
//                 OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
//                         NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
//                 );
            }
        }
    }
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tune_run) {
            //updateResult("Starting Autotune");
            int daysBack = Integer.parseInt(tune_days.getText().toString());
            if (daysBack > 0) {
//            resultView.setText(autotune.bgReadings(daysBack));
                resultView.setText(autotunePlugin.aapsAutotune(daysBack));
                tuneProfileSwitch.setVisibility(View.VISIBLE);
            } else
                resultView.setText("Set days between 1 and 10!!!");
            // lastrun in minutes ???
            warningView.setText("You already pressed RUN - NO WARNING NEEDED!");
            String latRunTxt = AutotunePlugin.lastRun != null ? "" + dateUtil.dateAndTimeString(AutotunePlugin.lastRun) : "";
            lastRunView.setText(latRunTxt);
        } else if (id == R.id.tune_profileswitch){
            String name = resourceHelper.gs(R.string.autotune_tunedprofile_name);
            ProfileStore profile = null;
            log("ProfileSwitch pressed");
            /*todo Profile management to update
            String profileString = SP.getString("autotuneprofile", null);
            if (profileString != null) {
                if (L.isEnabled(L.PROFILE))
                    log("Loaded profile: " + profileString);
                try {
                    profile = new ProfileStore(new JSONObject(profileString));
                } catch (JSONException e) {
                    log("Unhandled exception", e);
                    profile = null;
                }
            } else {
                if (L.isEnabled(L.PROFILE))
                    log("Stored profile not found");
            }

            if (profile != null) {
                final ProfileStore store = profile;
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
//                final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCH;
//                profileswitch.executeProfileSwitch = true;
//                newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
//                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");

// todo Philoul activate profile switch once AT is Ok (to update to be compatible with local profiles)
//                 OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
//                         NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
//                 );
            } else
                log.debug("ProfileStore is null!");
             */
        }

        //updateGUI();
    }

    // disabled by philoul to build AAPS
    //@Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {


                }
            });
    }


    //Todo add Strings and format text according to units
    private String addWarnings() {
        String warning = "";
        String nl = "";
        int toMgDl=1;
        if(profileFunction.getUnits().equals("mmol"))
            toMgDl = 18;
        ATProfile profile = new ATProfile(profileFunction.getProfile(System.currentTimeMillis()));
        if(!profile.isValid)
            return "Non profile selected";
        if (profile.getIcSize()>1) {
            warning = nl + "Autotune works with only one IC value, your profile has " + profile.getIcSize() + " values. Average value is " + profile.ic + "g/U";
            nl="\n";
        }
        if (profile.getIsfSize()>1) {
            warning = nl + "Autotune works with only one ISF value, your profile has " + profile.getIsfSize() + " values. Average value is " + profile.isf/toMgDl + profileFunction.getUnits() + "/U";
            nl="\n";
        }

        return warning;
    }

    //update if possible AutotuneFragment at beginning and during calculation between each day
    public void updateResult(String message) {
        resultView.setText(message);
        updateGUI();
    }

    private void log(String message) {
        autotunePlugin.atLog("Fragment] " + message);
    }

}
