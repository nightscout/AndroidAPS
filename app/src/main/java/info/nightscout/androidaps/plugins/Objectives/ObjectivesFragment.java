package info.nightscout.androidaps.plugins.Objectives;

import android.content.Context;
import android.content.SharedPreferences;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.APSResult;

public class ObjectivesFragment extends Fragment implements View.OnClickListener, PluginBase, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ObjectivesFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    CheckBox enableFakeTime;

    boolean fragmentVisible = true;

    String PREFS_NAME = "Objectives";

    @Override
    public int getType() {
        return PluginBase.CONSTRAINTS;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.objectives);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isVisibleInTabs() {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            default:
                break;
        }
    }

    class Objective {
        String objective;
        String gate;
        Date started;
        Integer durationInDays;
        Date accomplished;

        Objective(String objective, String gate, Date started, Integer durationInDays, Date accomplished) {
            this.objective = objective;
            this.gate = gate;
            this.started = started;
            this.durationInDays = durationInDays;
            this.accomplished = accomplished;
        }
    }

    private List<Objective> objectives;

    private void initializeData() {
        objectives = new ArrayList<>();
        objectives.add(new Objective("Setting up visualization and monitoring, and analyzing basals and ratios",
                "Verify that BG is available in Nightscout, and pump insulin data is being uploaded",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Starting on an open loop",
                "Run in Open Loop mode for a few days, and manually enact lots of temp basals",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Understanding your open loop, including its temp basal recommendations",
                "Based on that experience, decide what max basal should be, and set it on the pump",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Starting to close the loop with Low Glucose Suspend",
                "Run in closed loop with max IOB = 0 for a few days without too many LGS events",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Tuning the closed loop, raising max IOB above 0 and gradually lowering BG targets",
                "Run for a few days, and at least one night with no low BG alarms, before dropping BG",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
        objectives.add(new Objective("Adjust basals and ratios if needed, and then enable auto-sens",
                "1 week successful daytime looping with regular carb entry",
                new Date(0, 0, 0), 7, new Date(0, 0, 0)));
        objectives.add(new Objective("Enabling additional features for daytime use, such as advanced meal assist",
                "",
                new Date(0, 0, 0), 1, new Date(0, 0, 0)));
    }

    void saveProgress() {
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        for (int num = 0; num < objectives.size(); num++) {
            Objective o = objectives.get(num);
            editor.putLong(num + "started", o.started.getTime());
            editor.putLong(num + "accomplished", o.accomplished.getTime());
        }
        editor.commit();
        if (Config.logPrefsChange)
            log.debug("Objectives stored");
    }

    void loadProgress() {
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        for (int num = 0; num < objectives.size(); num++) {
            Objective o = objectives.get(num);
            o.started = new Date(settings.getLong(num + "started", 0));
            o.accomplished = new Date(settings.getLong(num + "accomplished", 0));
        }
        if (Config.logPrefsChange)
            log.debug("Objectives loaded");
    }

    boolean isAPSEnabledAtAll() {
        return true;
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ObjectiveViewHolder> {

        List<Objective> objectives;

        RecyclerViewAdapter(List<Objective> objectives) {
            this.objectives = objectives;
        }

        @Override
        public ObjectiveViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.objectives_item, viewGroup, false);
            ObjectiveViewHolder objectiveViewHolder = new ObjectiveViewHolder(v);
            return objectiveViewHolder;
        }

        @Override
        public void onBindViewHolder(ObjectiveViewHolder holder, int position) {
            Objective o = objectives.get(position);
            Context context = MainApp.instance().getApplicationContext();
            holder.position.setText(String.valueOf(position + 1));
            holder.objective.setText(o.objective);
            holder.gate.setText(o.gate);
            holder.duration.setText(context.getString(R.string.minimalduration) + " " + o.durationInDays + " " + context.getString(R.string.days));
            holder.started.setText(o.started.toLocaleString());
            holder.accomplished.setText(o.accomplished.toLocaleString());

            holder.startButton.setTag(o);
            holder.verifyButton.setTag(o);

            holder.startButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Objective o = (Objective) v.getTag();
                    o.started = new Date();
                    updateView();
                    //saveProgress();
                }
            });
            holder.verifyButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Objective o = (Objective) v.getTag();
                    o.accomplished = new Date();
                    updateView();
                    //saveProgress();
                }
            });

            Long now = new Date().getTime();
            if (position > 0 && objectives.get(position - 1).accomplished.getTime() == 0) {
                // Phase 0: previous not completed
                holder.startedLayout.setVisibility(View.GONE);
                holder.durationLayout.setVisibility(View.GONE);
                holder.verifyLayout.setVisibility(View.GONE);
            } else if (o.started.getTime() == 0) {
                // Phase 1: not started
                holder.durationLayout.setVisibility(View.GONE);
                holder.verifyLayout.setVisibility(View.GONE);
                holder.started.setVisibility(View.GONE);
            } else if (o.started.getTime() > 0 && !enableFakeTime.isChecked() && o.accomplished.getTime() == 0 && o.started.getTime() + o.durationInDays * 24 * 60 * 60 * 1000 > now) {
                // Phase 2: started, waiting for duration
                holder.startButton.setEnabled(false);
                holder.verifyLayout.setVisibility(View.GONE);
            } else if (o.accomplished.getTime() == 0 ) {
                // Phase 3: started, after duration
                holder.startButton.setEnabled(false);
                holder.accomplished.setVisibility(View.INVISIBLE);
            } else {
                // Phase 4: verified
                holder.gateLayout.setVisibility(View.GONE);
                holder.startedLayout.setVisibility(View.GONE);
                holder.durationLayout.setVisibility(View.GONE);
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

    public ObjectivesFragment() {
        super();
        initializeData();
        loadProgress();
    }

    public static ObjectivesFragment newInstance() {
        ObjectivesFragment fragment = new ObjectivesFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.objectives_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.objectives_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);
        enableFakeTime = (CheckBox) view.findViewById(R.id.objectives_faketime);
        enableFakeTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateView();
            }
        });
        updateView();

        return view;
    }

    void updateView() {
        RecyclerViewAdapter adapter = new RecyclerViewAdapter(objectives);
        recyclerView.setAdapter(adapter);
    }

    /**
     * Constraints interface
     **/
    @Override
    public boolean isClosedModeEnabled() {
        return true; // TODO: revert back
        //return objectives.get(3).started.getTime() > 0;
    }

    @Override
    public APSResult applyBasalConstraints(APSResult result) {
        return result;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        return absoluteRate;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        return percentRate;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        return carbs;
    }

}
