package info.nightscout.androidaps.plugins.Actions;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.plugins.Actions.dialogs.FillDialog;
import info.nightscout.androidaps.plugins.Actions.dialogs.NewExtendedBolusDialog;
import info.nightscout.androidaps.plugins.Actions.dialogs.NewTempBasalDialog;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;

/**
 * A simple {@link Fragment} subclass.
 */
public class ActionsFragment extends Fragment implements View.OnClickListener {

    static ActionsPlugin actionsPlugin = new ActionsPlugin();

    static public ActionsPlugin getPlugin() {
        return actionsPlugin;
    }

    Button profileSwitch;
    Button tempTarget;
    Button extendedBolus;
    Button tempBasal;
    Button fill;

    public ActionsFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.actions_fragment, container, false);

        profileSwitch = (Button) view.findViewById(R.id.actions_profileswitch);
        tempTarget = (Button) view.findViewById(R.id.actions_temptarget);
        extendedBolus = (Button) view.findViewById(R.id.actions_extendedbolus);
        tempBasal = (Button) view.findViewById(R.id.actions_settempbasal);
        fill = (Button) view.findViewById(R.id.actions_fill);

        profileSwitch.setOnClickListener(this);
        tempTarget.setOnClickListener(this);
        extendedBolus.setOnClickListener(this);
        tempBasal.setOnClickListener(this);
        fill.setOnClickListener(this);

        updateGUIIfVisible();
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged ev) {
        updateGUIIfVisible();
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        updateGUIIfVisible();
    }

    void updateGUIIfVisible() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!MainApp.getConfigBuilder().getPumpDescription().isSetBasalProfileCapable || !MainApp.getConfigBuilder().isInitialized() || MainApp.getConfigBuilder().isSuspended())
                        profileSwitch.setVisibility(View.GONE);
                    else
                        profileSwitch.setVisibility(View.VISIBLE);
                    if (!MainApp.getConfigBuilder().getPumpDescription().isExtendedBolusCapable || !MainApp.getConfigBuilder().isInitialized() || MainApp.getConfigBuilder().isSuspended())
                        extendedBolus.setVisibility(View.GONE);
                    else
                        extendedBolus.setVisibility(View.VISIBLE);
                    if (!MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable || !MainApp.getConfigBuilder().isInitialized() || MainApp.getConfigBuilder().isSuspended())
                        tempBasal.setVisibility(View.GONE);
                    else
                        tempBasal.setVisibility(View.VISIBLE);
                    if (!MainApp.getConfigBuilder().getPumpDescription().isRefillingCapable || !MainApp.getConfigBuilder().isInitialized() || MainApp.getConfigBuilder().isSuspended())
                        fill.setVisibility(View.GONE);
                    else
                        fill.setVisibility(View.VISIBLE);
                    if (!Config.APS)
                        tempTarget.setVisibility(View.GONE);
                    else
                        tempTarget.setVisibility(View.VISIBLE);
                }
            });
    }


    @Override
    public void onClick(View view) {
        FragmentManager manager = getFragmentManager();
        switch (view.getId()) {
            case R.id.actions_profileswitch:
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                newDialog.show(manager, "NewNSTreatmentDialog");
                break;
            case R.id.actions_temptarget:
                NewNSTreatmentDialog newTTDialog = new NewNSTreatmentDialog();
                final OptionsToShow temptarget = new OptionsToShow(R.id.careportal_temporarytarget, R.string.careportal_temporarytarget, false, false, false, false, true, false, false, false, false, true);
                temptarget.executeTempTarget = true;
                newTTDialog.setOptions(temptarget);
                newTTDialog.show(manager, "NewNSTreatmentDialog");
                break;
            case R.id.actions_extendedbolus:
                NewExtendedBolusDialog newExtendedDialog = new NewExtendedBolusDialog();
                newExtendedDialog.show(manager, "NewExtendedDialog");
                break;
            case R.id.actions_settempbasal:
                NewTempBasalDialog newTempDialog = new NewTempBasalDialog();
                newTempDialog.show(manager, "NewTempDialog");
                break;
            case R.id.actions_fill:
                FillDialog fillDialog = new FillDialog();
                fillDialog.show(manager, "FillDialog");
                break;
        }
    }
}
