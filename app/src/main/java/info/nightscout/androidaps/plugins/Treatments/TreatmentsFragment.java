package info.nightscout.androidaps.plugins.Treatments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsBolusFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsExtendedBolusesFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsTempTargetFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsTemporaryBasalsFragment;

public class TreatmentsFragment extends Fragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFragment.class);

    private static TreatmentsPlugin treatmentsPlugin = new TreatmentsPlugin();

    public static TreatmentsPlugin getPlugin() {
        return treatmentsPlugin;
    }

    Context context;
    TextView treatmentsTab;
    TextView extendedBolusesTab;
    TextView tempBasalsTab;
    TextView tempTargetTab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_fragment, container, false);

        treatmentsTab = (TextView) view.findViewById(R.id.treatments_treatments);
        extendedBolusesTab = (TextView) view.findViewById(R.id.treatments_extendedboluses);
        tempBasalsTab = (TextView) view.findViewById(R.id.treatments_tempbasals);
        tempTargetTab = (TextView) view.findViewById(R.id.treatments_temptargets);
        treatmentsTab.setOnClickListener(this);
        extendedBolusesTab.setOnClickListener(this);
        tempBasalsTab.setOnClickListener(this);
        tempTargetTab.setOnClickListener(this);
        context = getContext();

        setFragment(new TreatmentsBolusFragment());
        setBackgroundColorOnSelected(treatmentsTab);

        return view;
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.treatments_treatments:
                setFragment(new TreatmentsBolusFragment());
                setBackgroundColorOnSelected(treatmentsTab);
                break;
            case R.id.treatments_extendedboluses:
                setFragment(new TreatmentsExtendedBolusesFragment());
                setBackgroundColorOnSelected(extendedBolusesTab);
                break;
            case R.id.treatments_tempbasals:
                setFragment(new TreatmentsTemporaryBasalsFragment());
                setBackgroundColorOnSelected(tempBasalsTab);
                break;
            case R.id.treatments_temptargets:
                setFragment(new TreatmentsTempTargetFragment());
                setBackgroundColorOnSelected(tempTargetTab);
                break;
        }
    }

    private void setFragment(Fragment selectedFragment) {
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.replace(R.id.treatments_fragment_container, selectedFragment); // f2_container is your FrameLayout container
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.addToBackStack(null);
        ft.commit();
    }

    private void setBackgroundColorOnSelected(TextView selected) {
        treatmentsTab.setBackgroundColor(MainApp.sResources.getColor(R.color.defaultbackground));
        extendedBolusesTab.setBackgroundColor(MainApp.sResources.getColor(R.color.defaultbackground));
        tempBasalsTab.setBackgroundColor(MainApp.sResources.getColor(R.color.defaultbackground));
        tempTargetTab.setBackgroundColor(MainApp.sResources.getColor(R.color.defaultbackground));
        selected.setBackgroundColor(MainApp.sResources.getColor(R.color.tabBgColorSelected));
    }
}