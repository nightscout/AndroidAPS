package info.nightscout.androidaps.plugins.Actions;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;

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

        return view;
    }

    @Override
    public void onClick(View view) {
        FragmentManager manager = getFragmentManager();
        NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
        switch (view.getId()) {
            case R.id.actions_profileswitch:
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                break;
            default:
                newDialog = null;
        }
        if (newDialog != null)
            newDialog.show(manager, "NewNSTreatmentDialog");
    }
}
