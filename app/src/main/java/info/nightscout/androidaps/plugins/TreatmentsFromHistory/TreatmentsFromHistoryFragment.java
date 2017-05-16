package info.nightscout.androidaps.plugins.TreatmentsFromHistory;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsBolusFragment;
import info.nightscout.androidaps.plugins.Treatments.fragments.TreatmentsTempBasalsFragment;

public class TreatmentsFromHistoryFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFromHistoryFragment.class);

    private static TreatmentsFromHistoryPlugin treatmentsPlugin = new TreatmentsFromHistoryPlugin();

    public static TreatmentsFromHistoryPlugin getPlugin() {
        return treatmentsPlugin;
    }

    Context context;

    Fragment bolusFragment;
    Fragment tempBasalsFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_fragment, container, false);

        bolusFragment = new TreatmentsBolusFragment();
        tempBasalsFragment = new TreatmentsTempBasalsFragment();

        context = getContext();

        return view;
    }
}
