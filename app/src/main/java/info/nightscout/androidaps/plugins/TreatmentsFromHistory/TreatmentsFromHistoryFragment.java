package info.nightscout.androidaps.plugins.TreatmentsFromHistory;

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

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.TreatmentsFromHistory.fragments.TreatmentsBolusFragment;
import info.nightscout.androidaps.plugins.TreatmentsFromHistory.fragments.TreatmentsExtendedBolusesFragment;
import info.nightscout.androidaps.plugins.TreatmentsFromHistory.fragments.TreatmentsTemporaryBasalsFragment;

public class TreatmentsFromHistoryFragment extends Fragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFromHistoryFragment.class);

    private static TreatmentsFromHistoryPlugin treatmentsPlugin = new TreatmentsFromHistoryPlugin();

    public static TreatmentsFromHistoryPlugin getPlugin() {
        return treatmentsPlugin;
    }

    Context context;
    TextView treatmentsTab;
    TextView extendedBolusesTab;
    TextView tempBasalsTab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_fragment, container, false);

        treatmentsTab = (TextView) view.findViewById(R.id.treatments_treatments);
        extendedBolusesTab = (TextView) view.findViewById(R.id.treatments_extendedboluses);
        tempBasalsTab = (TextView) view.findViewById(R.id.treatments_tempbasals);
        treatmentsTab.setOnClickListener(this);
        extendedBolusesTab.setOnClickListener(this);
        tempBasalsTab.setOnClickListener(this);
        context = getContext();

        setFragment(new TreatmentsBolusFragment());

        return view;
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.treatments_treatments:
                setFragment(new TreatmentsBolusFragment());
                break;
            case R.id.treatments_extendedboluses:
                setFragment(new TreatmentsExtendedBolusesFragment());
                break;
            case R.id.treatments_tempbasals:
                setFragment(new TreatmentsTemporaryBasalsFragment());
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
}