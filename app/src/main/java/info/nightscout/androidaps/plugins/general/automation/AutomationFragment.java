package info.nightscout.androidaps.plugins.general.automation;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseTriggerDialog;
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditActionDialog;
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;

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
        mEventListAdapter = new EventListAdapter(plugin.getAutomationEvents(), getFragmentManager());
        mEventListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mEventListView.setAdapter(mEventListAdapter);

        EditEventDialog.setOnClickListener(event -> mEventListAdapter.notifyDataSetChanged());

        updateGUI();

        return view;
    }

    @Override
    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> mEventListAdapter.notifyDataSetChanged());
    }

    @OnClick(R.id.fabAddEvent)
    void onClickAddEvent(View v) {
        EditEventDialog dialog = EditEventDialog.newInstance(new AutomationEvent(), true);
        dialog.show(getFragmentManager(), "EditEventDialog");
    }

    /**
     * RecyclerViewAdapter to display event lists.
     */
    public static class EventListAdapter extends RecyclerView.Adapter<EventListAdapter.ViewHolder>  {
        private final List<AutomationEvent> mEventList;
        private final FragmentManager mFragmentManager;

        public EventListAdapter(List<AutomationEvent> events, FragmentManager fragmentManager) {
            this.mEventList = events;
            this.mFragmentManager = fragmentManager;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.automation_event_item, parent, false);
            return new ViewHolder(v, parent.getContext());
        }

        private void addImage(@DrawableRes int res, Context context, LinearLayout layout) {
            ImageView iv = new ImageView(context);
            iv.setImageResource(res);
            iv.setLayoutParams(new LinearLayout.LayoutParams(MainApp.dpToPx(24),MainApp.dpToPx(24)));
            layout.addView(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final AutomationEvent event = mEventList.get(position);
            holder.eventTitle.setText(event.getTitle());
            holder.iconLayout.removeAllViews();

            // trigger icons
            HashSet<Integer> triggerIcons = new HashSet<>();
            TriggerConnector.fillIconSet((TriggerConnector)event.getTrigger(), triggerIcons);
            for(int res : triggerIcons) {
                addImage(res, holder.context, holder.iconLayout);
            }

            // arrow icon
            ImageView iv = new ImageView(holder.context);
            iv.setImageResource(R.drawable.ic_arrow_forward_white_24dp);
            iv.setLayoutParams(new LinearLayout.LayoutParams(MainApp.dpToPx(24),MainApp.dpToPx(24)));
            iv.setPadding(MainApp.dpToPx(4), 0, MainApp.dpToPx(4), 0);
            holder.iconLayout.addView(iv);

            // action icons
            HashSet<Integer> actionIcons = new HashSet<>();
            for(Action action : event.getActions()) {
                if (action.icon().isPresent())
                    actionIcons.add(action.icon().get());
            }
            for(int res : actionIcons) {
                addImage(res, holder.context, holder.iconLayout);
            }

            // action: remove
            holder.iconTrash.setOnClickListener(v -> {
                mEventList.remove(event);
                notifyDataSetChanged();
            });

            // action: edit
            holder.rootLayout.setOnClickListener(v -> {
                EditEventDialog dialog = EditEventDialog.newInstance(event, false);
                dialog.show(mFragmentManager, "EditEventDialog");
            });
        }

        @Override
        public int getItemCount() {
            return mEventList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final RelativeLayout rootLayout;
            final LinearLayout iconLayout;
            final TextView eventTitle;
            final Context context;
            final ImageView iconTrash;

            public ViewHolder(View view, Context context) {
                super(view);
                this.context = context;
                eventTitle = view.findViewById(R.id.viewEventTitle);
                rootLayout = view.findViewById(R.id.rootLayout);
                iconLayout = view.findViewById(R.id.iconLayout);
                iconTrash =  view.findViewById(R.id.iconTrash);
            }
        }
    }

    /**
     * RecyclerViewAdapter to display event lists.
     */
    public static class ActionListAdapter extends RecyclerView.Adapter<ActionListAdapter.ViewHolder>  {
        private final List<Action> mActionList;
        private final FragmentManager mFragmentManager;

        public ActionListAdapter(FragmentManager manager, List<Action> events) {
            this.mActionList = events;
            this.mFragmentManager = manager;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.automation_action_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Action action = mActionList.get(position);
            holder.actionTitle.setText(action.friendlyName());
            holder.itemRoot.setOnClickListener(v -> {
                if (action.hasDialog()) {
                    EditActionDialog dialog = EditActionDialog.newInstance(action);
                    dialog.show(mFragmentManager, "EditActionDialog");
                }
            });
        }

        @Override
        public int getItemCount() {
            return mActionList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView actionTitle;
            TextView actionDescription;
            LinearLayout itemRoot;

            public ViewHolder(View view) {
                super(view);
                itemRoot = view.findViewById(R.id.itemRoot);
                actionTitle = view.findViewById(R.id.viewActionTitle);
                actionDescription = view.findViewById(R.id.viewActionDescription);
            }
        }
    }

    /**
     * Custom Adapter to display triggers dynamically with nested linear layouts.
     */
    public static class TriggerListAdapter {
        private final LinearLayout mRootLayout;
        private final Context mContext;
        private final TriggerConnector mRootConnector;
        private final FragmentManager mFragmentManager;

        public TriggerListAdapter(Context context, FragmentManager fragmentManager, LinearLayout rootLayout, TriggerConnector rootTrigger) {
            mRootLayout = rootLayout;
            mContext = context;
            mFragmentManager = fragmentManager;
            mRootConnector = rootTrigger;
            build();
        }

        public Context getContext() {
            return mContext;
        }

        public LinearLayout getRootLayout() {
            return mRootLayout;
        }

        public FragmentManager getFragmentManager() {
            return mFragmentManager;
        }

        public void destroy() {
            mRootLayout.removeAllViews();
        }

        private void build() {
            for(int i = 0; i < mRootConnector.size(); ++i) {
                final Trigger trigger = mRootConnector.get(i);

                // spinner
                if (i > 0) {
                    createSpinner(trigger);
                }

                // trigger layout
                mRootLayout.addView(trigger.createView(mContext, mFragmentManager));

                // buttons
                createButtons(trigger);
            }

            if (mRootConnector.size() == 0) {
                Button buttonAdd = new Button(mContext);
                buttonAdd.setText("Add New");
                buttonAdd.setOnClickListener(v -> {
                    ChooseTriggerDialog dialog = ChooseTriggerDialog.newInstance();
                    dialog.setOnClickListener(newTriggerObject -> {
                        mRootConnector.add(newTriggerObject);
                        rebuild();
                    });
                    dialog.show(mFragmentManager, "ChooseTriggerDialog");
                });
                mRootLayout.addView(buttonAdd);
            }
        }

        private Spinner createSpinner() {
            Spinner spinner = new Spinner(mContext);
            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, TriggerConnector.Type.labels());
            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(spinnerArrayAdapter);
            return spinner;
        }

        private void createSpinner(Trigger trigger) {
            final TriggerConnector connector = trigger.getConnector();
            final int initialPosition = connector.getConnectorType().ordinal();
            Spinner spinner = createSpinner();
            spinner.setSelection(initialPosition);
            spinner.setBackgroundColor(MainApp.gc(R.color.black_overlay));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, MainApp.dpToPx(8), 0, MainApp.dpToPx(8));
            spinner.setLayoutParams(params);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position != initialPosition) {
                        // conector type changed
                        changeConnector(trigger, connector, TriggerConnector.Type.values()[position]);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
            });
            mRootLayout.addView(spinner);
        }

        private void createButtons(Trigger trigger) {
            // do not create buttons for TriggerConnector
            if (trigger instanceof TriggerConnector) {
                return;
            }

            // Button Layout
            LinearLayout buttonLayout = new LinearLayout(mContext);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mRootLayout.addView(buttonLayout);

            // Button [-]
            Button buttonRemove = new Button(mContext);
            buttonRemove.setText("del");
            buttonRemove.setOnClickListener(v -> {
                final TriggerConnector connector = trigger.getConnector();
                connector.remove(trigger);
                connector.simplify().rebuildView();
            });
            buttonLayout.addView(buttonRemove);

            // Button [+]
            Button buttonAdd = new Button(mContext);
            buttonAdd.setText("add");
            buttonAdd.setOnClickListener(v -> {
                ChooseTriggerDialog dialog = ChooseTriggerDialog.newInstance();
                dialog.show(mFragmentManager, "ChooseTriggerDialog");
                dialog.setOnClickListener(newTriggerObject -> {
                    TriggerConnector connector = trigger.getConnector();
                    connector.add(connector.pos(trigger)+1, newTriggerObject);
                    connector.simplify().rebuildView();
                });
            });
            buttonLayout.addView(buttonAdd);

            // Button [*]
            Button buttonCopy = new Button(mContext);
            buttonCopy.setText("copy");
            buttonCopy.setOnClickListener(v -> {
                TriggerConnector connector = trigger.getConnector();
                connector.add(connector.pos(trigger)+1, trigger.duplicate());
                connector.simplify().rebuildView();
            });
            buttonLayout.addView(buttonCopy);
        }

        public static void changeConnector(final Trigger trigger, final TriggerConnector connector, final TriggerConnector.Type newConnectorType) {
            if (connector.size() > 2) {
                // split connector
                int pos = connector.pos(trigger) - 1;

                TriggerConnector newConnector = new TriggerConnector(newConnectorType);

                // move trigger from pos and pos+1 into new connector
                for(int i = 0; i < 2; ++i) {
                    Trigger t = connector.get(pos);
                    newConnector.add(t);
                    connector.remove(t);
                }

                connector.add(pos, newConnector);
            } else {
                connector.changeConnectorType(newConnectorType);
            }

            connector.simplify().rebuildView();
        }

        public void rebuild() {
            destroy();
            build();
        }
    }

}
