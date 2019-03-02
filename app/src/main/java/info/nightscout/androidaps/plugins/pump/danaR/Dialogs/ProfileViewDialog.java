package info.nightscout.androidaps.plugins.pump.danaR.dialogs;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.fragments.ProfileGraph;
import info.nightscout.androidaps.utils.DecimalFormatter;

/**
 * Created by mike on 10.07.2016.
 */
public class ProfileViewDialog extends DialogFragment {
    private  TextView noProfile;
    private  TextView units;
    private  TextView dia;
    private  TextView activeProfile;
    private  TextView ic;
    private  TextView isf;
    private  TextView basal;
    private  TextView target;
    private ProfileGraph basalGraph;


    private  Button refreshButton;

    public ProfileViewDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.profileviewer_fragment, container, false);

        noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
        units = (TextView) layout.findViewById(R.id.profileview_units);
        dia = (TextView) layout.findViewById(R.id.profileview_dia);
        activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
        ic = (TextView) layout.findViewById(R.id.profileview_ic);
        isf = (TextView) layout.findViewById(R.id.profileview_isf);
        basal = (TextView) layout.findViewById(R.id.profileview_basal);
        target = (TextView) layout.findViewById(R.id.profileview_target);
        refreshButton = (Button) layout.findViewById(R.id.profileview_reload);
        refreshButton.setVisibility(View.VISIBLE);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("ProfileViewDialog", null);
                dismiss();
            }
        });
        basalGraph = (ProfileGraph) layout.findViewById(R.id.basal_graph);
        setContent();
        return layout;
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void setContent() {
        ProfileStore store = ((ProfileInterface)ConfigBuilderPlugin.getPlugin().getActivePump()).getProfile();
        if (store != null) {
            noProfile.setVisibility(View.GONE);
            Profile profile = store.getDefaultProfile();
            units.setText(profile.getUnits());
            dia.setText(DecimalFormatter.to2Decimal(profile.getDia()) + " h");
            activeProfile.setText(((ProfileInterface) ConfigBuilderPlugin.getPlugin().getActivePump()).getProfileName());
            ic.setText(profile.getIcList());
            isf.setText(profile.getIsfList());
            basal.setText(profile.getBasalList());
            target.setText(profile.getTargetList());
            basalGraph.show(store.getDefaultProfile());
        } else {
            noProfile.setVisibility(View.VISIBLE);
        }
    }


}
