package info.nightscout.androidaps.plugins.general.automation;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog;

public class AutomationFragment extends SubscriberFragment {

    private RecyclerView mEventListView;
    private FloatingActionButton mFabAddEvent;
    private EventListAdapter mEventListAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.automation_fragment, container, false);

        mEventListView = view.findViewById(R.id.eventListView);
        mFabAddEvent = view.findViewById(R.id.fabAddEvent);

        mFabAddEvent.setOnClickListener(this::onClickAddEvent);

        final AutomationPlugin plugin = AutomationPlugin.getPlugin();
        mEventListAdapter = new EventListAdapter(plugin.getAutomationEvents());
        mEventListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mEventListView.setAdapter(mEventListAdapter);

        updateGUI();

        return view;
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mEventListAdapter.notifyDataSetChanged();
            });
    }

    private void onClickAddEvent(View v) {
        /*EditEventDialog dialog = EditEventDialog.newInstance();
        FragmentManager manager = getFragmentManager();
        dialog.show(manager, "EditEventDialog");*/

        final AutomationPlugin plugin = AutomationPlugin.getPlugin();
        plugin.getAutomationEvents().add(new AutomationEvent("Test"));
        updateGUI();
    }

    public static class EventListAdapter extends RecyclerView.Adapter<EventListAdapter.ViewHolder>  {
        private final List<AutomationEvent> mEvents;

        EventListAdapter(List<AutomationEvent> events) {
            this.mEvents = events;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.automation_event_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final AutomationEvent event = mEvents.get(position);
            holder.eventTitle.setText(event.getTitle());
        }

        @Override
        public int getItemCount() {
            return mEvents.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView eventTitle;

            public ViewHolder(View itemView) {
                super(itemView);
                eventTitle = itemView.findViewById(R.id.viewEventTitle);
            }
        }
    }

}
