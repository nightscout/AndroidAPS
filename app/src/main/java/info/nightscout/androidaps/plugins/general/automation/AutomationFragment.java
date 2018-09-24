package info.nightscout.androidaps.plugins.general.automation;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;

public class AutomationFragment extends SubscriberFragment {

    @BindView(R.id.eventListView)
    RecyclerView mEventListView;

    private EventListAdapter mEventListAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.automation_fragment, container, false);
        unbinder = ButterKnife.bind(this, view);

        final AutomationPlugin plugin = AutomationPlugin.getPlugin();
        mEventListAdapter = new EventListAdapter(plugin.getAutomationEvents());
        mEventListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mEventListView.setAdapter(mEventListAdapter);

        updateGUI();

        return view;
    }

    @Override
    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mEventListAdapter.notifyDataSetChanged();
            });
    }

    @OnClick(R.id.fabAddEvent)
    void onClickAddEvent(View v) {
        EditEventDialog dialog = EditEventDialog.newInstance(new AutomationEvent());
        FragmentManager manager = getFragmentManager();
        dialog.show(manager, "EditEventDialog");
    }

    /**
     * RecyclerViewAdapter to display event lists.
     */
    public static class EventListAdapter extends RecyclerView.Adapter<EventListAdapter.ViewHolder>  {
        private final List<AutomationEvent> mEventList;

        EventListAdapter(List<AutomationEvent> events) {
            this.mEventList = events;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.automation_event_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final AutomationEvent event = mEventList.get(position);
            holder.eventTitle.setText(event.getTitle());

            // TODO: check null
            holder.eventDescription.setText(event.getTrigger().friendlyDescription());
        }

        @Override
        public int getItemCount() {
            return mEventList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView eventTitle;
            TextView eventDescription;

            public ViewHolder(View view) {
                super(view);
                eventTitle = view.findViewById(R.id.viewEventTitle);
                eventDescription = view.findViewById(R.id.viewEventDescription);
            }
        }
    }

    /**
     * Custom Adapter to display triggers dynamically with nested linear layouts.
     */
    public static class TriggerListAdapter {
        private final LinearLayout mRootLayout;
        private final LayoutInflater mInflater;
        private final List<Trigger> mTriggerList;

        public TriggerListAdapter(LayoutInflater inflater, LinearLayout rootLayout, List<Trigger> triggers) {
            mRootLayout = rootLayout;
            mInflater = inflater;
            mTriggerList = triggers;
            build();
        }

        public TriggerListAdapter(LayoutInflater inflater, LinearLayout rootLayout, Trigger trigger) {
            mRootLayout = rootLayout;
            mInflater = inflater;
            mTriggerList = new ArrayList<>();
            mTriggerList.add(trigger);
            build();
        }

        public void destroy() {
            mRootLayout.removeAllViews();
            for(Trigger trigger : mTriggerList) {
                trigger.destroyViewHolder();
            }
        }

        private void build() {
            for(Trigger trigger : mTriggerList) {
                Trigger.ViewHolder viewHolder = trigger.createViewHolder(mInflater);
                mRootLayout.addView(viewHolder.getView());
            }
        }

        public void rebuild() {
            destroy();
            build();
        }
    }

}
