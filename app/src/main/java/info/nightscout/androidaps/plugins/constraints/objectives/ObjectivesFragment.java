package info.nightscout.androidaps.plugins.constraints.objectives;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;

public class ObjectivesFragment extends SubscriberFragment {
    private RecyclerView recyclerView;
    private CheckBox enableFake;
    private TextView reset;
    private ObjectivesAdapter objectivesAdapter = new ObjectivesAdapter();
    private Handler handler = new Handler(Looper.getMainLooper());

    private Runnable objectiveUpdater = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, 60 * 1000);
            updateGUI();
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
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
                ObjectivesPlugin.getPlugin().saveProgress();
                recyclerView.getAdapter().notifyDataSetChanged();
                scrollToCurrentObjective();
            });
            scrollToCurrentObjective();
            startUpdateTimer();
            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Override
    public synchronized void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(objectiveUpdater);
    }

    private void startUpdateTimer() {
        handler.removeCallbacks(objectiveUpdater);
        for (Objective objective : ObjectivesPlugin.getPlugin().getObjectives()) {
            if (objective.isStarted() && !objective.isAccomplished()) {
                long timeTillNextMinute = (System.currentTimeMillis() - objective.getStartedOn()) % (60 * 1000);
                handler.postDelayed(objectiveUpdater, timeTillNextMinute);
                break;
            }
        }
    }

    private void scrollToCurrentObjective() {
        for (int i = 0; i < ObjectivesPlugin.getPlugin().getObjectives().size(); i++) {
            Objective objective = ObjectivesPlugin.getPlugin().getObjectives().get(i);
            if (!objective.isStarted() || !objective.isAccomplished()) {
                RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
                    @Override
                    protected int getVerticalSnapPreference() {
                        return LinearSmoothScroller.SNAP_TO_START;
                    }

                    @Override
                    protected int calculateTimeForScrolling(int dx) {
                        return super.calculateTimeForScrolling(dx) * 4;
                    }
                };
                smoothScroller.setTargetPosition(i);
                recyclerView.getLayoutManager().startSmoothScroll(smoothScroller);
                break;
            }
        }
    }

    private class ObjectivesAdapter extends RecyclerView.Adapter<ObjectivesAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.objectives_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Objective objective = ObjectivesPlugin.getPlugin().getObjectives().get(position);
            holder.title.setText(MainApp.gs(R.string.nth_objective, position + 1));
            holder.revert.setVisibility(View.INVISIBLE);
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
                holder.accomplished.setVisibility(View.GONE);
                if (position == 0 || ObjectivesPlugin.getPlugin().getObjectives().get(position - 1).isAccomplished())
                    holder.start.setVisibility(View.VISIBLE);
                else holder.start.setVisibility(View.GONE);
            } else if (objective.isAccomplished()) {
                holder.gate.setTextColor(0xFF4CAF50);
                holder.verify.setVisibility(View.GONE);
                holder.progress.setVisibility(View.GONE);
                holder.start.setVisibility(View.GONE);
                holder.accomplished.setVisibility(View.VISIBLE);
            } else if (objective.isStarted()) {
                holder.gate.setTextColor(0xFFFFFFFF);
                holder.verify.setVisibility(View.VISIBLE);
                holder.verify.setEnabled(objective.isCompleted() || enableFake.isChecked());
                holder.start.setVisibility(View.GONE);
                holder.accomplished.setVisibility(View.GONE);
                if (objective.isRevertable()) {
                    holder.revert.setVisibility(View.VISIBLE);
                }
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
            holder.accomplished.setText(MainApp.gs(R.string.accomplished, DateUtil.dateAndTimeString(objective.getAccomplishedOn())));
            holder.accomplished.setTextColor(0xFFC1C1C1);
            holder.verify.setOnClickListener((view) -> {
                objective.setAccomplishedOn(DateUtil.now());
                notifyDataSetChanged();
                scrollToCurrentObjective();
                startUpdateTimer();
            });
            holder.start.setOnClickListener((view) -> {
                objective.setStartedOn(DateUtil.now());
                notifyDataSetChanged();
                scrollToCurrentObjective();
                startUpdateTimer();
            });
            holder.revert.setOnClickListener((view) -> {
                objective.setAccomplishedOn(0);
                objective.setStartedOn(0);
                if (position > 0) {
                    Objective prevObj = ObjectivesPlugin.getPlugin().getObjectives().get(position - 1);
                    prevObj.setAccomplishedOn(0);
                }
                notifyDataSetChanged();
                scrollToCurrentObjective();
            });
            if (objective.hasSpecialInput && !objective.isAccomplished()) {
                holder.enterButton.setVisibility(View.VISIBLE);
                holder.input.setVisibility(View.VISIBLE);
                holder.inputHint.setVisibility(View.VISIBLE);
                holder.enterButton.setOnClickListener((view) -> {
                    String input = holder.input.getText().toString();
                    objective.specialAction(getActivity(), input);
                    notifyDataSetChanged();
                });
            } else {
                holder.enterButton.setVisibility(View.GONE);
                holder.input.setVisibility(View.GONE);
                holder.inputHint.setVisibility(View.GONE);
            }
        }


        @Override
        public int getItemCount() {
            return ObjectivesPlugin.getPlugin().getObjectives().size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            CardView cardView;
            public TextView title;
            public TextView objective;
            TextView gate;
            TextView accomplished;
            public LinearLayout progress;
            Button verify;
            public Button start;
            Button revert;
            TextView inputHint;
            EditText input;
            Button enterButton;

            ViewHolder(View itemView) {
                super(itemView);
                cardView = (CardView) itemView;
                title = itemView.findViewById(R.id.objective_title);
                objective = itemView.findViewById(R.id.objective_objective);
                gate = itemView.findViewById(R.id.objective_gate);
                progress = itemView.findViewById(R.id.objective_progress);
                verify = itemView.findViewById(R.id.objective_verify);
                start = itemView.findViewById(R.id.objective_start);
                revert = itemView.findViewById(R.id.objective_back);
                accomplished = itemView.findViewById(R.id.objective_accomplished);
                inputHint = itemView.findViewById(R.id.objective_inputhint);
                input = itemView.findViewById(R.id.objective_input);
                enterButton = itemView.findViewById(R.id.objective_enterbutton);
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
