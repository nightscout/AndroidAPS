package info.nightscout.androidaps.plugins.Treatments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsBolusFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsCareportalFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsExtendedBolusesFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsProfileSwitchFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsTempTargetFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsTemporaryBasalsFragment;
import info.nightscout.utils.FabricPrivacy;

public class TreatmentsFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFragment.class);

    TextView treatmentsTab;
    TextView extendedBolusesTab;
    TextView tempBasalsTab;
    TextView tempTargetTab;
    TextView profileSwitchTab;
    TextView careportalTab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.treatments_fragment, container, false);

            treatmentsTab = (TextView) view.findViewById(R.id.treatments_treatments);
            extendedBolusesTab = (TextView) view.findViewById(R.id.treatments_extendedboluses);
            tempBasalsTab = (TextView) view.findViewById(R.id.treatments_tempbasals);
            tempTargetTab = (TextView) view.findViewById(R.id.treatments_temptargets);
            profileSwitchTab = (TextView) view.findViewById(R.id.treatments_profileswitches);
            careportalTab = (TextView) view.findViewById(R.id.treatments_careportal);
            treatmentsTab.setOnClickListener(this);
            extendedBolusesTab.setOnClickListener(this);
            tempBasalsTab.setOnClickListener(this);
            tempTargetTab.setOnClickListener(this);
            profileSwitchTab.setOnClickListener(this);
            careportalTab.setOnClickListener(this);

            setFragment(new TreatmentsBolusFragment());
            setBackgroundColorOnSelected(treatmentsTab);

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;

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
            case R.id.treatments_profileswitches:
                setFragment(new TreatmentsProfileSwitchFragment());
                setBackgroundColorOnSelected(profileSwitchTab);
                break;
            case R.id.treatments_careportal:
                setFragment(new TreatmentsCareportalFragment());
                setBackgroundColorOnSelected(careportalTab);
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
        profileSwitchTab.setBackgroundColor(MainApp.sResources.getColor(R.color.defaultbackground));
        careportalTab.setBackgroundColor(MainApp.sResources.getColor(R.color.defaultbackground));
        selected.setBackgroundColor(MainApp.sResources.getColor(R.color.tabBgColorSelected));
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        if (ConfigBuilderPlugin.getActivePump().getPumpDescription().isExtendedBolusCapable
                || TreatmentsPlugin.getPlugin().getExtendedBolusesFromHistory().size() > 0) {
            extendedBolusesTab.setVisibility(View.VISIBLE);
        } else {
            extendedBolusesTab.setVisibility(View.GONE);
        }
    }
}