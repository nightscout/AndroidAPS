package info.nightscout.androidaps.plugins.general.automation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog;
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.ItemTouchHelperAdapter;
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.ItemTouchHelperViewHolder;
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.OnStartDragListener;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;
import info.nightscout.androidaps.utils.OKDialog;

class EventListAdapter extends RecyclerView.Adapter<EventListAdapter.ViewHolder> implements ItemTouchHelperAdapter {
    private final List<AutomationEvent> eventList;
    private final FragmentManager fragmentManager;
    private final Activity activity;

    private final OnStartDragListener mDragStartListener;

    EventListAdapter(List<AutomationEvent> events, FragmentManager fragmentManager, Activity activity, OnStartDragListener dragStartListener) {
        this.eventList = events;
        this.fragmentManager = fragmentManager;
        this.activity = activity;
        mDragStartListener = dragStartListener;
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
        iv.setLayoutParams(new LinearLayout.LayoutParams(MainApp.dpToPx(24), MainApp.dpToPx(24)));
        layout.addView(iv);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final AutomationEvent event = eventList.get(position);
        holder.eventTitle.setText(event.getTitle());
        holder.enabled.setChecked(event.isEnabled());
        holder.iconLayout.removeAllViews();

        // trigger icons
        HashSet<Integer> triggerIcons = new HashSet<>();
        TriggerConnector.fillIconSet((TriggerConnector) event.getTrigger(), triggerIcons);
        for (int res : triggerIcons) {
            addImage(res, holder.context, holder.iconLayout);
        }

        // arrow icon
        ImageView iv = new ImageView(holder.context);
        iv.setImageResource(R.drawable.ic_arrow_forward_white_24dp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(MainApp.dpToPx(24), MainApp.dpToPx(24)));
        iv.setPadding(MainApp.dpToPx(4), 0, MainApp.dpToPx(4), 0);
        holder.iconLayout.addView(iv);

        // action icons
        HashSet<Integer> actionIcons = new HashSet<>();
        for (Action action : event.getActions()) {
            if (action.icon().isPresent())
                actionIcons.add(action.icon().get());
        }
        for (int res : actionIcons) {
            addImage(res, holder.context, holder.iconLayout);
        }

        // enabled event
        holder.enabled.setOnClickListener(v -> {
            event.setEnabled((holder.enabled.isChecked()));
            RxBus.INSTANCE.send(new EventAutomationDataChanged());
        });

        // edit event
        holder.rootLayout.setOnClickListener(v ->

        {
            //EditEventDialog dialog = EditEventDialog.Companion.newInstance(event, false);
            EditEventDialog dialog = new EditEventDialog();
            Bundle args = new Bundle();
            args.putString("event", event.toJSON());
            args.putInt("position", position);
            dialog.setArguments(args);
            if (fragmentManager != null)
                dialog.show(fragmentManager, "EditEventDialog");
        });

        // Start a drag whenever the handle view it touched
        holder.iconSort.setOnTouchListener((v, motionEvent) ->

        {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                mDragStartListener.onStartDrag(holder);
                return true;
            }
            return v.onTouchEvent(motionEvent);
        });
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        Collections.swap(eventList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        RxBus.INSTANCE.send(new EventAutomationDataChanged());
        return true;
    }

    @Override
    public void onItemDismiss(int position) {
        OKDialog.showConfirmation(activity, MainApp.gs(R.string.removerecord) + " " + eventList.get(position).getTitle(),
                () -> {
                    eventList.remove(position);
                    notifyItemRemoved(position);
                    RxBus.INSTANCE.send(new EventAutomationDataChanged());
                    RxBus.INSTANCE.send(new EventAutomationUpdateGui());
                }, () -> {
                    RxBus.INSTANCE.send(new EventAutomationUpdateGui());
                });
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements ItemTouchHelperViewHolder {
        final RelativeLayout rootLayout;
        final LinearLayout iconLayout;
        final TextView eventTitle;
        final Context context;
        final ImageView iconSort;
        final CheckBox enabled;

        ViewHolder(View view, Context context) {
            super(view);
            this.context = context;
            eventTitle = view.findViewById(R.id.viewEventTitle);
            rootLayout = view.findViewById(R.id.rootLayout);
            iconLayout = view.findViewById(R.id.iconLayout);
            iconSort = view.findViewById(R.id.iconSort);
            enabled = view.findViewById(R.id.automation_enabled);
        }

        @Override
        public void onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY);
        }

        @Override
        public void onItemClear() {
            itemView.setBackgroundColor(MainApp.gc(R.color.ribbonDefault));
        }
    }
}
