package info.nightscout.androidaps.plugins.ConstraintsObjectives;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives.Objective;
import info.nightscout.utils.FabricPrivacy;

public class ObjectivesFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(ObjectivesFragment.class);

    RecyclerView recyclerView;
    CheckBox enableFake;
    TextView reset;
    ObjectivesAdapter objectivesAdapter = new ObjectivesAdapter();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.objectives_fragment, container, false);

            recyclerView = view.findViewById(R.id.objectives_recyclerview);
            recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
            recyclerView.setAdapter(objectivesAdapter);
            enableFake = view.findViewById(R.id.objectives_fake);
            reset = view.findViewById(R.id.objectives_reset);
            enableFake.setOnClickListener(v -> updateGUI());
            reset.setOnClickListener(v -> {
                ObjectivesPlugin.getPlugin().reset();
                ObjectivesPlugin.saveProgress();
                updateGUI();
            });

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    private class ObjectivesAdapter extends RecyclerView.Adapter<ObjectivesAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.objectives_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Objective objective = ObjectivesPlugin.getObjectives().get(position);
            holder.title.setText(MainApp.gs(R.string.nth_objective, position + 1));
            if (objective.getObjective() != 0) {
                holder.objective.setVisibility(View.VISIBLE);
                holder.objective.setText(MainApp.gs(objective.getObjective()));
            } else holder.objective.setVisibility(View.GONE);
            if (objective.getGate() != 0) {
                holder.gate.setVisibility(View.VISIBLE);
                holder.gate.setText(MainApp.gs(objective.getGate()));
            } else holder.gate.setVisibility(View.GONE);
            if (!objective.isStarted()) {
                holder.gate.setTextColor(0xFFFFFFFF);
                holder.verify.setVisibility(View.GONE);
                holder.progress.setVisibility(View.GONE);
                if (position == 0 || ObjectivesPlugin.getObjectives().get(position - 1).isAccomplished())
                    holder.start.setVisibility(View.VISIBLE);
                else holder.start.setVisibility(View.GONE);
            } else if (objective.isAccomplished()) {
                holder.gate.setTextColor(0xFF4CAF50);
                holder.verify.setVisibility(View.GONE);
                holder.progress.setVisibility(View.GONE);
                holder.start.setVisibility(View.GONE);
            } else if (objective.isStarted()) {
                holder.gate.setTextColor(0xFFFFFFFF);
                holder.verify.setVisibility(View.VISIBLE);
                holder.verify.setEnabled(objective.isAccomplished() || enableFake.isChecked());
                holder.start.setVisibility(View.GONE);
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.removeAllViews();
                for (Objective.Task task : objective.getTasks()) {
                    if (task.shouldBeIgnored()) continue;
                    TextView textView = new TextView(holder.progress.getContext());
                    textView.setTextColor(0xFFFFFFFF);
                    String basicHTML = "%2$s: <font color=\"%1$s\"><b>%3$s</b></font>";
                    String formattedHTML = String.format(basicHTML, task.isCompleted() ? "#4CAF50" : "#FF9800", MainApp.gs(task.getTask()), task.getProgress());
                    textView.setText(Html.fromHtml(formattedHTML));
                    holder.progress.addView(textView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                }
            }
            holder.verify.setOnClickListener((view) -> {
                objective.setAccomplishedOn(new Date());
                updateGUI();
            });
            holder.start.setOnClickListener((view) -> {
                objective.setStartedOn(new Date());
                updateGUI();
            });
        }

        @Override
        public int getItemCount() {
            return ObjectivesPlugin.getObjectives().size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public CardView cardView;
            public TextView title;
            public TextView objective;
            public TextView gate;
            public LinearLayout progress;
            public Button verify;
            public Button start;

            public ViewHolder(View itemView) {
                super(itemView);
                cardView = (CardView) itemView;
                title = itemView.findViewById(R.id.objective_title);
                objective = itemView.findViewById(R.id.objective_objective);
                gate = itemView.findViewById(R.id.objective_gate);
                progress = itemView.findViewById(R.id.objective_progress);
                verify = itemView.findViewById(R.id.objective_verify);
                start = itemView.findViewById(R.id.objective_start);
            }
        }
    }

    @Override
    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                objectivesAdapter.notifyDataSetChanged();
            });
    }

}
