package info.nightscout.androidaps.plugins.PumpInsight;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.utils.StatusItem;
import info.nightscout.androidaps.plugins.PumpInsight.utils.ui.StatusItemViewAdapter;


public class InsightPumpFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(InsightPumpFragment.class);
    private static Handler sLoopHandler = new Handler();
    private static Runnable sRefreshLoop = null;

    StatusItemViewAdapter viewAdapter;
    LinearLayout holder;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (sRefreshLoop == null) {
            sRefreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
                }
            };
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.insightpump_fragment, container, false);
            holder = view.findViewById(R.id.insightholder);
            viewAdapter = new StatusItemViewAdapter(getActivity(), holder);

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventInsightPumpUpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        final Activity activity = getActivity();
        if (activity != null && holder != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final InsightPumpPlugin insightPumpPlugin = InsightPumpPlugin.getPlugin();
                    final List<StatusItem> l = insightPumpPlugin.getStatusItems();

                    holder.removeAllViews();

                    for (StatusItem row : l) {
                        viewAdapter.inflateStatus(row);
                    }

                }
            });
    }
}
