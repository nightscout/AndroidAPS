package info.nightscout.androidaps.plugins.general.automation.dialogs;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui;

public class ActionListAdapter extends RecyclerView.Adapter<ActionListAdapter.ViewHolder> {
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
        holder.actionTitle.setText(action.shortDescription());
        holder.layoutText.setOnClickListener(v -> {
            if (action.hasDialog()) {
                EditActionDialog dialog = EditActionDialog.newInstance(action);
                dialog.show(mFragmentManager, "EditActionDialog");
            }
        });
        holder.iconTrash.setOnClickListener(v -> {
            mActionList.remove(action);
            notifyDataSetChanged();
            MainApp.bus().post(new EventAutomationUpdateGui());
        });
    }

    @Override
    public int getItemCount() {
        return mActionList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView actionTitle;
        TextView actionDescription;
        LinearLayout layoutText;
        ImageView iconTrash;

        ViewHolder(View view) {
            super(view);
            layoutText = view.findViewById(R.id.layoutText);
            actionTitle = view.findViewById(R.id.viewActionTitle);
            actionDescription = view.findViewById(R.id.viewActionDescription);
            iconTrash = view.findViewById(R.id.iconTrash);
        }
    }
}
