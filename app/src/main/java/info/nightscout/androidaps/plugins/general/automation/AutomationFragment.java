package info.nightscout.androidaps.plugins.general.automation;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.otto.Subscribe;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui;

public class AutomationFragment extends SubscriberFragment {

    @BindView(R.id.eventListView)
    RecyclerView mEventListView;
    @BindView(R.id.logView)
    TextView mLogView;

    private EventListAdapter mEventListAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.automation_fragment, container, false);
        unbinder = ButterKnife.bind(this, view);

        final AutomationPlugin plugin = AutomationPlugin.getPlugin();
        mEventListAdapter = new EventListAdapter(plugin.getAutomationEvents(), getFragmentManager());
        mEventListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mEventListView.setAdapter(mEventListAdapter);

        return view;
    }

    @Subscribe
    public void onEvent(EventAutomationUpdateGui unused) {
        updateGUI();
    }

    @Subscribe
    public void onEvent(EventAutomationDataChanged unused) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mEventListAdapter.notifyDataSetChanged();
            });
    }

    @Override
    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mEventListAdapter.notifyDataSetChanged();
                StringBuilder sb = new StringBuilder();
                for (String l : AutomationPlugin.getPlugin().executionLog) {
                    sb.append(l);
                    sb.append("\n");
                }
                mLogView.setText(sb.toString());
            });
    }

    @OnClick(R.id.fabAddEvent)
    void onClickAddEvent(View unused) {
        EditEventDialog dialog = EditEventDialog.newInstance(new AutomationEvent(), true);
        if (getFragmentManager() != null)
            dialog.show(getFragmentManager(), "EditEventDialog");
    }

}
