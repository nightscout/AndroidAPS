package info.nightscout.androidaps.plugins.Treatments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsBolusFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsTempBasalsFragment;

public class TreatmentsFragment extends Fragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFragment.class);

    private static TreatmentsPlugin treatmentsPlugin = new TreatmentsPlugin();

    public static TreatmentsPlugin getPlugin() {
        return treatmentsPlugin;
    }

    Context context;
    TextView treatmentsTab;
    TextView tempBasalsTab;

    Fragment bolusFragment;
    Fragment tempBasalsFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_fragment, container, false);

        bolusFragment = new TreatmentsBolusFragment();
        tempBasalsFragment = new TreatmentsTempBasalsFragment();

        treatmentsTab = (TextView) view.findViewById(R.id.treatments_treatments);
        tempBasalsTab = (TextView) view.findViewById(R.id.treatments_tempbasals);
        treatmentsTab.setOnClickListener(this);
        tempBasalsTab.setOnClickListener(this);
        context = getContext();

        setFragment(bolusFragment);

        return view;
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.treatments_treatments:
                setFragment(bolusFragment);
                break;
            case R.id.treatments_tempbasals:
                setFragment(tempBasalsFragment);
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
