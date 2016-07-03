package info.nightscout.androidaps.plugins.Objectives;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Loop.APSResult;

public class ObjectivesFragment extends Fragment implements View.OnClickListener, PluginBase, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ObjectivesFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    CheckBox enableFake; // TODO: remove faking

    boolean fragmentVisible = true;

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
        Integer num;
        String objective;
        String gate;
        Date started;
        Integer durationInDays;
        Date accomplished;

        Objective(Integer num, String objective, String gate, Date started, Integer durationInDays, Date accomplished) {
            this.num = num;
            this.objective = objective;
            this.gate = gate;
            this.started = started;
            this.durationInDays = durationInDays;
            this.accomplished = accomplished;
        }
    }

    // Objective 0
    public boolean bgIsAvailableInNS = false;
    public boolean pumpStatusIsAvailableInNS = false;
    // Objective 1
    public Integer manualEnacts = 0;
    public final Integer manualEnactsNeeded = 20;

    class RequirementResult {
        boolean done = false;
        String comment = "";

        public RequirementResult(boolean done, String comment) {
            this.done = done;
            this.comment = comment;
        }
    }

    private String yesOrNo(boolean yes) {
        if (yes) return "☺";
        else return "---";
    }

    private RequirementResult requirementsMet(Integer objNum) {
        switch (objNum) {
            case 0:
                return new RequirementResult(bgIsAvailableInNS && pumpStatusIsAvailableInNS,
                        getString(R.string.bgavailableinns) + ": " + yesOrNo(bgIsAvailableInNS)
                                + " " + getString(R.string.pumpstatusavailableinns) + ": " + yesOrNo(pumpStatusIsAvailableInNS));
            case 1:
                return new RequirementResult(manualEnacts >= manualEnactsNeeded,
                        getString(R.string.manualenacts) + ": " + manualEnacts + "/" + manualEnactsNeeded);
            case 2:
                return new RequirementResult(true, "");
            default:
                return new RequirementResult(false, "");
        }
    }


    private List<Objective> objectives;

    private void initializeData() {
        objectives = new ArrayList<>();
        objectives.add(new Objective(0,
                "Setting up visualization and monitoring, and analyzing basals and ratios",
                "Verify that BG is available in Nightscout, and pump insulin data is being uploaded",
                new Date(0, 0, 0),
                1, // 1 day
                new Date(0, 0, 0)));
        objectives.add(new Objective(1,
                "Starting on an open loop",
                "Run in Open Loop mode for a few days, and manually enact lots of temp basals",
                new Date(0, 0, 0),
                7, // 7 days
                new Date(0, 0, 0)));
        objectives.add(new Objective(2,
                "Understanding your open loop, including its temp basal recommendations",
                "Based on that experience, decide what max basal should be, and set it on the pump and preferences",
                new Date(0, 0, 0),
                0, // 0 days
                new Date(0, 0, 0)));
        objectives.add(new Objective(3,
                "Starting to close the loop with Low Glucose Suspend",
                "Run in closed loop with max IOB = 0 for a few days without too many LGS events",
                new Date(0, 0, 0),
                5, // 5 days
                new Date(0, 0, 0)));
        objectives.add(new Objective(4,
                "Tuning the closed loop, raising max IOB above 0 and gradually lowering BG targets",
                "Run for a few days, and at least one night with no low BG alarms, before dropping BG",
                new Date(0, 0, 0),
                1,
                new Date(0, 0, 0)));
        objectives.add(new Objective(5,
                "Adjust basals and ratios if needed, and then enable auto-sens",
                "1 week successful daytime looping with regular carb entry",
                new Date(0, 0, 0),
                7,
                new Date(0, 0, 0)));
        objectives.add(new Objective(6,
                "Enabling additional features for daytime use, such as advanced meal assist",
                "",
                new Date(0, 0, 0),
                1,
                new Date(0, 0, 0)));
    }

    public void saveProgress() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        for (int num = 0; num < objectives.size(); num++) {
            Objective o = objectives.get(num);
            editor.putLong("Objectives" + num + "started", o.started.getTime());
            editor.putLong("Objectives" + num + "accomplished", o.accomplished.getTime());
        }
        editor.putBoolean("Objectives" + "bgIsAvailableInNS", bgIsAvailableInNS);
        editor.putBoolean("Objectives" + "pumpStatusIsAvailableInNS", pumpStatusIsAvailableInNS);
        editor.putInt("Objectives" + "manualEnacts", manualEnacts);
        editor.commit();
        if (Config.logPrefsChange)
            log.debug("Objectives stored");
    }

    void loadProgress() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        for (int num = 0; num < objectives.size(); num++) {
            Objective o = objectives.get(num);
            o.started = new Date(settings.getLong("Objectives" + num + "started", 0));
            o.accomplished = new Date(settings.getLong("Objectives" + num + "accomplished", 0));
        }
        bgIsAvailableInNS = settings.getBoolean("Objectives" + "bgIsAvailableInNS", false);
        pumpStatusIsAvailableInNS = settings.getBoolean("Objectives" + "pumpStatusIsAvailableInNS", false);
        manualEnacts = settings.getInt("Objectives" + "manualEnacts", 0);
        if (Config.logPrefsChange)
            log.debug("Objectives loaded");
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ObjectiveViewHolder> {

        List<Objective> objectives;

        RecyclerViewAdapter(List<Objective> objectives) {
            this.objectives = objectives;
        }

        @Override
        public ObjectiveViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.objectives_item, viewGroup, false);
            return new ObjectiveViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ObjectiveViewHolder holder, int position) {
            Objective o = objectives.get(position);
            RequirementResult requirementsMet = requirementsMet(position);
            Context context = MainApp.instance().getApplicationContext();
            holder.position.setText(String.valueOf(position + 1));
            holder.objective.setText(o.objective);
            holder.gate.setText(o.gate);
            holder.duration.setText(context.getString(R.string.minimalduration) + " " + o.durationInDays + " " + context.getString(R.string.days));
            holder.progress.setText(requirementsMet.comment);
            holder.started.setText(o.started.toLocaleString());
            holder.accomplished.setText(o.accomplished.toLocaleString());

            holder.startButton.setTag(o);
            holder.verifyButton.setTag(o);

            holder.startButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Objective o = (Objective) v.getTag();
                    o.started = new Date();
                    updateGUI();
                    saveProgress();
                }
            });
            holder.verifyButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Objective o = (Objective) v.getTag();
                    if (requirementsMet(o.num).done || enableFake.isChecked()) {
                        o.accomplished = new Date();
                        updateGUI();
                        saveProgress();
                    }
                }
            });

            Long now = new Date().getTime();
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
            } else if (o.started.getTime() > 0 && !enableFake.isChecked() && o.accomplished.getTime() == 0 && o.started.getTime() + o.durationInDays * 24 * 60 * 60 * 1000 > now && !requirementsMet.done) {
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

    public ObjectivesFragment() {
        super();
        initializeData();
        loadProgress();
        registerBus();
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
        enableFake = (CheckBox) view.findViewById(R.id.objectives_fake);
        enableFake.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateGUI();
            }
        });
        updateGUI();

        return view;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    RecyclerViewAdapter adapter = new RecyclerViewAdapter(objectives);
                    recyclerView.setAdapter(adapter);
                }
            });
    }

    /**
     * Constraints interface
     **/
    @Override
    public boolean isLoopEnabled() {
        return objectives.get(1).started.getTime() > 0;
    }

    @Override
    public boolean isClosedModeEnabled() {
        return objectives.get(3).started.getTime() > 0;
    }

    @Override
    public boolean isAutosensModeEnabled() {
        return objectives.get(5).started.getTime() > 0;
    }

    @Override
    public boolean isAMAModeEnabled() {
        return objectives.get(6).started.getTime() > 0;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        if (objectives.get(4).started.getTime() > 0)
            return maxIob;
        else {
            if (Config.logConstraintsChanges)
                log.debug("Limiting maxIOB " + maxIob + " to " + 0 + "U");
            return 0d;
        }
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
