package info.nightscout.androidaps.plugins.ConstraintsObjectives;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class ObjectivesFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(ObjectivesFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    CheckBox enableFake;
    LinearLayout fake_layout;
    TextView reset;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ObjectiveViewHolder> {

        List<ObjectivesPlugin.Objective> objectives;

        RecyclerViewAdapter(List<ObjectivesPlugin.Objective> objectives) {
            this.objectives = objectives;
        }

        @Override
        public ObjectiveViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.objectives_item, viewGroup, false);
            return new ObjectiveViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ObjectiveViewHolder holder, int position) {
            ObjectivesPlugin.Objective o = objectives.get(position);
            ObjectivesPlugin.RequirementResult requirementsMet = ObjectivesPlugin.getPlugin().requirementsMet(position);
            Context context = MainApp.instance().getApplicationContext();
            holder.position.setText(String.valueOf(position + 1));
            holder.objective.setText(o.objective);
            holder.gate.setText(o.gate);
            holder.duration.setText(context.getString(R.string.objectives_minimalduration) + " " + o.durationInDays + " " + context.getString(R.string.days));
            holder.progress.setText(requirementsMet.comment);
            holder.started.setText(o.started.toLocaleString());
            holder.accomplished.setText(o.accomplished.toLocaleString());

            holder.startButton.setTag(o);
            holder.verifyButton.setTag(o);

            holder.startButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ObjectivesPlugin.Objective o = (ObjectivesPlugin.Objective) v.getTag();
                    o.started = new Date();
                    updateGUI();
                    ObjectivesPlugin.saveProgress();
                }
            });
            holder.verifyButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ObjectivesPlugin.Objective o = (ObjectivesPlugin.Objective) v.getTag();
                    if (ObjectivesPlugin.getPlugin().requirementsMet(o.num).done || enableFake.isChecked()) {
                        o.accomplished = new Date();
                        updateGUI();
                        ObjectivesPlugin.saveProgress();
                    }
                }
            });

            Long now = System.currentTimeMillis();
            if (position > 0 && objectives.get(position - 1).accomplished.getTime() == 0) {
                // Phase 0: previous not completed
                holder.startedLayout.setVisibility(View.GONE);
                holder.durationLayout.setVisibility(View.GONE);
                holder.progressLayout.setVisibility(View.GONE);
                holder.verifyLayout.setVisibility(View.GONE);
            } else if (o.started.getTime() == 0) {
                // Phase 1: not started
                holder.durationLayout.setVisibility(View.GONE);
                holder.progressLayout.setVisibility(View.GONE);
                holder.verifyLayout.setVisibility(View.GONE);
                holder.started.setVisibility(View.GONE);
            } else if (o.started.getTime() > 0 && !enableFake.isChecked() && o.accomplished.getTime() == 0 && !(o.started.getTime() + o.durationInDays * 24 * 60 * 60 * 1000 < now && requirementsMet.done)) {
                // Phase 2: started, waiting for duration and met requirements
                holder.startButton.setEnabled(false);
                holder.verifyLayout.setVisibility(View.GONE);
            } else if (o.accomplished.getTime() == 0) {
                // Phase 3: started, after duration, requirements met
                holder.startButton.setEnabled(false);
                holder.accomplished.setVisibility(View.INVISIBLE);
            } else {
                // Phase 4: verified
                holder.gateLayout.setVisibility(View.GONE);
                holder.startedLayout.setVisibility(View.GONE);
                holder.durationLayout.setVisibility(View.GONE);
                holder.progressLayout.setVisibility(View.GONE);
                holder.verifyButton.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return objectives.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ObjectiveViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView position;
            TextView objective;
            LinearLayout gateLayout;
            TextView gate;
            TextView duration;
            LinearLayout durationLayout;
            TextView progress;
            LinearLayout progressLayout;
            TextView started;
            Button startButton;
            LinearLayout startedLayout;
            TextView accomplished;
            Button verifyButton;
            LinearLayout verifyLayout;

            ObjectiveViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.objectives_cardview);
                position = (TextView) itemView.findViewById(R.id.objectives_position);
                objective = (TextView) itemView.findViewById(R.id.objectives_objective);
                durationLayout = (LinearLayout) itemView.findViewById(R.id.objectives_duration_linearlayout);
                duration = (TextView) itemView.findViewById(R.id.objectives_duration);
                progressLayout = (LinearLayout) itemView.findViewById(R.id.objectives_progresslayout);
                progress = (TextView) itemView.findViewById(R.id.objectives_progress);
                gateLayout = (LinearLayout) itemView.findViewById(R.id.objectives_gate_linearlayout);
                gate = (TextView) itemView.findViewById(R.id.objectives_gate);
                startedLayout = (LinearLayout) itemView.findViewById(R.id.objectives_start_linearlayout);
                started = (TextView) itemView.findViewById(R.id.objectives_started);
                startButton = (Button) itemView.findViewById(R.id.objectives_start);
                verifyLayout = (LinearLayout) itemView.findViewById(R.id.objectives_verify_linearlayout);
                accomplished = (TextView) itemView.findViewById(R.id.objectives_accomplished);
                verifyButton = (Button) itemView.findViewById(R.id.objectives_verify);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.objectives_fragment, container, false);

            recyclerView = (RecyclerView) view.findViewById(R.id.objectives_recyclerview);
            recyclerView.setHasFixedSize(true);
            llm = new LinearLayoutManager(view.getContext());
            recyclerView.setLayoutManager(llm);
            enableFake = (CheckBox) view.findViewById(R.id.objectives_fake);
            fake_layout = (LinearLayout) view.findViewById(R.id.objectives_fake_layout);
            reset = (TextView) view.findViewById(R.id.objectives_reset);
            enableFake.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    updateGUI();
                }
            });
            reset.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ObjectivesPlugin.getPlugin().initializeData();
                    ObjectivesPlugin.saveProgress();
                    updateGUI();
                }
            });

            // Add correct translations to array after app is initialized
            ObjectivesPlugin.objectives.get(0).objective = MainApp.sResources.getString(R.string.objectives_0_objective);
            ObjectivesPlugin.objectives.get(1).objective = MainApp.sResources.getString(R.string.objectives_1_objective);
            ObjectivesPlugin.objectives.get(2).objective = MainApp.sResources.getString(R.string.objectives_2_objective);
            ObjectivesPlugin.objectives.get(3).objective = MainApp.sResources.getString(R.string.objectives_3_objective);
            ObjectivesPlugin.objectives.get(4).objective = MainApp.sResources.getString(R.string.objectives_4_objective);
            ObjectivesPlugin.objectives.get(5).objective = MainApp.sResources.getString(R.string.objectives_5_objective);
            ObjectivesPlugin.objectives.get(6).objective = MainApp.sResources.getString(R.string.objectives_6_objective);
            ObjectivesPlugin.objectives.get(0).gate = MainApp.sResources.getString(R.string.objectives_0_gate);
            ObjectivesPlugin.objectives.get(1).gate = MainApp.sResources.getString(R.string.objectives_1_gate);
            ObjectivesPlugin.objectives.get(2).gate = MainApp.sResources.getString(R.string.objectives_2_gate);
            ObjectivesPlugin.objectives.get(3).gate = MainApp.sResources.getString(R.string.objectives_3_gate);
            ObjectivesPlugin.objectives.get(4).gate = MainApp.sResources.getString(R.string.objectives_4_gate);
            ObjectivesPlugin.objectives.get(5).gate = MainApp.sResources.getString(R.string.objectives_5_gate);
            updateGUI();

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    RecyclerViewAdapter adapter = new RecyclerViewAdapter(ObjectivesPlugin.objectives);
                    recyclerView.setAdapter(adapter);
                }
            });
    }

}
