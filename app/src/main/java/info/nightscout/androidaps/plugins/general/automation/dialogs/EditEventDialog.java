package info.nightscout.androidaps.plugins.general.automation.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.squareup.otto.Subscribe;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent;
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationAddAction;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateTrigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;

public class EditEventDialog extends DialogFragment {
    public interface OnClickListener {
        void onClick(AutomationEvent event);
    }

    private static AutomationEvent staticEvent;

    @BindView(R.id.inputEventTitle)
    TextInputEditText mEditEventTitle;

    @BindView(R.id.editTrigger)
    TextView mEditTrigger;

    @BindView(R.id.addAction)
    TextView mAddAction;

    @BindView(R.id.triggerDescription)
    TextView mTriggerDescription;

    @BindView(R.id.forcedtriggerdescription)
    TextView mForcedTriggerDescription;

    @BindView(R.id.forcedtriggerdescriptionlabel)
    TextView mForcedTriggerDescriptionLabel;

    @BindView(R.id.actionListView)
    RecyclerView mActionListView;

    private Unbinder mUnbinder;
    private ActionListAdapter mActionListAdapter;
    private AutomationEvent mEvent;
    private boolean mAddNew;

    public static EditEventDialog newInstance(AutomationEvent event, boolean addNew) {
        staticEvent = event;

        Bundle args = new Bundle();
        EditEventDialog fragment = new EditEventDialog();
        fragment.setArguments(args);
        // clone event
        try {
            fragment.mEvent = new AutomationEvent().fromJSON(event.toJSON());
        } catch (Exception e) {
            e.printStackTrace();
        }
        fragment.mAddNew = addNew;
        return fragment;
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.automation_dialog_event, container, false);
        mUnbinder = ButterKnife.bind(this, view);

        // load data from bundle
        if (savedInstanceState != null) {
            String eventData = savedInstanceState.getString("event");
            if (eventData != null) mEvent = new AutomationEvent().fromJSON(eventData);
            mAddNew = savedInstanceState.getBoolean("addNew");
        } else if (mAddNew) {
            mEvent.setTrigger(new TriggerConnector(TriggerConnector.Type.OR));

        }

        // event title
        mEditEventTitle.setText(mEvent.getTitle());

        // display root trigger
        mTriggerDescription.setText(mEvent.getTrigger().friendlyDescription());

        mEditTrigger.setOnClickListener(v -> {
            EditTriggerDialog dialog = EditTriggerDialog.newInstance(mEvent.getTrigger());
            if (getFragmentManager() != null)
                dialog.show(getFragmentManager(), "EditTriggerDialog");
        });

        // setup action list view
        mActionListAdapter = new ActionListAdapter(getFragmentManager(), mEvent.getActions());
        mActionListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mActionListView.setAdapter(mActionListAdapter);

        mAddAction.setOnClickListener(v -> {
            FragmentManager fragmentManager = getFragmentManager();
            if (fragmentManager != null)
                new ChooseActionDialog().show(fragmentManager, "ChooseActionDialog");
        });

        showPreconditions();

        MainApp.bus().register(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        MainApp.bus().unregister(this);
        mUnbinder.unbind();
        super.onDestroyView();
    }

    @Subscribe
    public void onEventAutomationUpdateGui(EventAutomationUpdateGui ignored) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mActionListAdapter.notifyDataSetChanged();
                showPreconditions();
            });
    }

    @Subscribe
    public void onEventAutomationAddAction(EventAutomationAddAction event) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mEvent.addAction(event.getAction());
                mActionListAdapter.notifyDataSetChanged();
            });
    }

    @Subscribe
    public void onEventAutomationUpdateTrigger(EventAutomationUpdateTrigger event) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                mEvent.setTrigger(event.getTrigger());
                mTriggerDescription.setText(mEvent.getTrigger().friendlyDescription());
            });
    }

    @OnClick(R.id.ok)
    public void onButtonOk(View unused) {
        // check for title
        String title = mEditEventTitle.getText().toString();
        if (title.isEmpty()) {
            Toast.makeText(getContext(), R.string.automation_missing_task_name, Toast.LENGTH_LONG).show();
            return;
        }
        mEvent.setTitle(title);

        // check for at least one trigger
        TriggerConnector con = (TriggerConnector) mEvent.getTrigger();
        if (con.size() == 0) {
            Toast.makeText(getContext(), R.string.automation_missing_trigger, Toast.LENGTH_LONG).show();
            return;
        }

        // check for at least one action
        if (mEvent.getActions().isEmpty()) {
            Toast.makeText(getContext(), R.string.automation_missing_action, Toast.LENGTH_LONG).show();
            return;
        }

        // apply changes
        staticEvent.fromJSON(mEvent.toJSON());

        // add new
        if (mAddNew) {
            final AutomationPlugin plugin = AutomationPlugin.getPlugin();
            plugin.getAutomationEvents().add(mEvent);
        }

        dismiss();
        MainApp.bus().post(new EventAutomationDataChanged());
    }

    @OnClick(R.id.cancel)
    public void onButtonCancel(View unused) {
        dismiss();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putString("event", mEvent.toJSON());
        bundle.putBoolean("addNew", mAddNew);
    }

    private void showPreconditions() {
        TriggerConnector forcedTriggers = mEvent.getPreconditions();
        if (forcedTriggers.size() > 0) {
            mForcedTriggerDescription.setVisibility(View.VISIBLE);
            mForcedTriggerDescriptionLabel.setVisibility(View.VISIBLE);
            mForcedTriggerDescription.setText(forcedTriggers.friendlyDescription());
        } else {
            mForcedTriggerDescription.setVisibility(View.GONE);
            mForcedTriggerDescriptionLabel.setVisibility(View.GONE);
        }
    }
}
