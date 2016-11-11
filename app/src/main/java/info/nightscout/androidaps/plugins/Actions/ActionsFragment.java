package info.nightscout.androidaps.plugins.Actions;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.Actions.dialogs.FillDialog;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.Actions.dialogs.NewExtendedBolusDialog;
import info.nightscout.androidaps.plugins.Actions.dialogs.NewTempBasalDialog;

/**
 * A simple {@link Fragment} subclass.
 */
public class ActionsFragment extends Fragment implements FragmentBase, View.OnClickListener {

    static ActionsPlugin actionsPlugin = new ActionsPlugin();
    static public ActionsPlugin getPlugin() {
        return actionsPlugin;
    }

    public ActionsFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.actions_fragment, container, false);

        view.findViewById(R.id.actions_profileswitch).setOnClickListener(this);
        view.findViewById(R.id.actions_extendedbolus).setOnClickListener(this);
        view.findViewById(R.id.actions_settempbasal).setOnClickListener(this);
        view.findViewById(R.id.actions_fill).setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {
        FragmentManager manager = getFragmentManager();
        switch (view.getId()) {
            case R.id.actions_profileswitch:
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                newDialog.show(manager, "NewNSTreatmentDialog");
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
