package info.nightscout.androidaps.plugins.PumpInsight;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;


import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.utils.StatusItem;
import info.nightscout.androidaps.plugins.PumpInsight.utils.ui.StatusItemViewAdapter;
import info.nightscout.utils.FabricPrivacy;


public class InsightFragment extends SubscriberFragment {
    private static final Logger log = LoggerFactory.getLogger(InsightFragment.class);
    private static final Handler sLoopHandler = new Handler();
    private static volatile boolean refresh = false;
    private static volatile boolean pending = false;
    StatusItemViewAdapter viewAdapter;
    LinearLayout holder;
    private final Runnable sRefreshLoop = new Runnable() {
        @Override
        public void run() {
            pending = false;
            updateGUI();
            if (refresh) {
                scheduleRefresh();
            }
        }
    };

    private synchronized void scheduleRefresh() {
        if (!pending) {
            pending = true;
            sLoopHandler.postDelayed(sRefreshLoop, 30 * 1000L);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            final View view = inflater.inflate(R.layout.insightpump_fragment, container, false);
            holder = (LinearLayout) view.findViewById(R.id.insightholder);
            viewAdapter = new StatusItemViewAdapter(getActivity(), holder);

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }


    @Override
    public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        if (visible) {
            refresh = true;
            pending = false;
            updateGUI();
            scheduleRefresh();
        } else {
            refresh = false;
            //sLoopHandler.removeCallbacksAndMessages(null);
        }
    }


    @Subscribe
    public void onStatusEvent(final EventInsightUpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        final Activity activity = getActivity();
        if (activity != null && holder != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final InsightPlugin insightPlugin = InsightPlugin.getPlugin();
                    final List<StatusItem> l = insightPlugin.getStatusItems(refresh);

                    holder.removeAllViews();

                    for (StatusItem row : l) {
                        viewAdapter.inflateStatus(row);
                    }

                }
            });
    }
}
