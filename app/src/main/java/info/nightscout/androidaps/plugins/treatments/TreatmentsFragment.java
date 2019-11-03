package info.nightscout.androidaps.plugins.treatments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsBolusFragment;
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsCareportalFragment;
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsExtendedBolusesFragment;
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsProfileSwitchFragment;
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTempTargetFragment;
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTemporaryBasalsFragment;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class TreatmentsFragment extends Fragment implements View.OnClickListener {
    private CompositeDisposable disposable = new CompositeDisposable();

    TextView treatmentsTab;
    TextView extendedBolusesTab;
    TextView tempBasalsTab;
    TextView tempTargetTab;
    TextView profileSwitchTab;
    TextView careportalTab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventExtendedBolusChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
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
        treatmentsTab.setBackgroundColor(MainApp.gc(R.color.defaultbackground));
        extendedBolusesTab.setBackgroundColor(MainApp.gc(R.color.defaultbackground));
        tempBasalsTab.setBackgroundColor(MainApp.gc(R.color.defaultbackground));
        tempTargetTab.setBackgroundColor(MainApp.gc(R.color.defaultbackground));
        profileSwitchTab.setBackgroundColor(MainApp.gc(R.color.defaultbackground));
        careportalTab.setBackgroundColor(MainApp.gc(R.color.defaultbackground));
        selected.setBackgroundColor(MainApp.gc(R.color.tabBgColorSelected));
    }

    private void updateGui() {
        if (ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().isExtendedBolusCapable
                || TreatmentsPlugin.getPlugin().getExtendedBolusesFromHistory().size() > 0) {
            extendedBolusesTab.setVisibility(View.VISIBLE);
        } else {
            extendedBolusesTab.setVisibility(View.GONE);
        }
    }
}