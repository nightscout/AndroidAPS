package info.nightscout.androidaps.plugins.Objectives;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

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
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 05.08.2016.
 */
public class ObjectivesPlugin implements PluginBase, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ObjectivesPlugin.class);

    public static List<Objective> objectives;

    boolean fragmentVisible = true;

    public ObjectivesPlugin() {
        initializeData();
        loadProgress();
        MainApp.bus().register(this);
    }

    @Override
    public String getFragmentClass() {
        return ObjectivesFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.CONSTRAINTS;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.objectives);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == CONSTRAINTS && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        LoopPlugin loopPlugin = (LoopPlugin) MainApp.getSpecificPlugin(LoopPlugin.class);
        return type == CONSTRAINTS && fragmentVisible && loopPlugin != null && loopPlugin.isVisibleInTabs(LOOP);
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == CONSTRAINTS) this.fragmentVisible = fragmentVisible;
    }

    public class Objective {
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
    public static boolean bgIsAvailableInNS = false;
    public static boolean pumpStatusIsAvailableInNS = false;
    // Objective 1
    public static Integer manualEnacts = 0;
    public static final Integer manualEnactsNeeded = 20;

    public class RequirementResult {
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

    public RequirementResult requirementsMet(Integer objNum) {
        switch (objNum) {
            case 0:
                return new RequirementResult(bgIsAvailableInNS && pumpStatusIsAvailableInNS,
                        MainApp.sResources.getString(R.string.objectives_bgavailableinns) + ": " + yesOrNo(bgIsAvailableInNS)
                                + " " + MainApp.sResources.getString(R.string.objectives_pumpstatusavailableinns) + ": " + yesOrNo(pumpStatusIsAvailableInNS));
            case 1:
                return new RequirementResult(manualEnacts >= manualEnactsNeeded,
                        MainApp.sResources.getString(R.string.objectives_manualenacts) + ": " + manualEnacts + "/" + manualEnactsNeeded);
            case 2:
                return new RequirementResult(true, "");
            default:
                return new RequirementResult(true, "");
        }
    }


    public void initializeData() {
        bgIsAvailableInNS = false;
        pumpStatusIsAvailableInNS = false;
        manualEnacts = 0;

        objectives = new ArrayList<>();
        objectives.add(new Objective(0,
                MainApp.sResources.getString(R.string.objectives_0_objective),
                MainApp.sResources.getString(R.string.objectives_0_gate),
                new Date(0),
                1, // 1 day
                new Date(0)));
        objectives.add(new Objective(1,
                MainApp.sResources.getString(R.string.objectives_1_objective),
                MainApp.sResources.getString(R.string.objectives_1_gate),
                new Date(0),
                7, // 7 days
                new Date(0)));
        objectives.add(new Objective(2,
                MainApp.sResources.getString(R.string.objectives_2_objective),
                MainApp.sResources.getString(R.string.objectives_2_gate),
                new Date(0),
                0, // 0 days
                new Date(0)));
        objectives.add(new Objective(3,
                MainApp.sResources.getString(R.string.objectives_3_objective),
                MainApp.sResources.getString(R.string.objectives_3_gate),
                new Date(0),
                5, // 5 days
                new Date(0)));
        objectives.add(new Objective(4,
                MainApp.sResources.getString(R.string.objectives_4_objective),
                MainApp.sResources.getString(R.string.objectives_4_gate),
                new Date(0),
                1,
                new Date(0)));
        objectives.add(new Objective(5,
                MainApp.sResources.getString(R.string.objectives_5_objective),
                MainApp.sResources.getString(R.string.objectives_5_gate),
                new Date(0),
                7,
                new Date(0)));
        objectives.add(new Objective(6,
                MainApp.sResources.getString(R.string.objectives_6_objective),
                "",
                new Date(0),
                14,
                new Date(0)));
    }

    public static void saveProgress() {
        if (objectives != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            SharedPreferences.Editor editor = settings.edit();
            for (int num = 0; num < objectives.size(); num++) {
                Objective o = objectives.get(num);
                editor.putString("Objectives" + num + "started", Long.toString(o.started.getTime()));
                editor.putString("Objectives" + num + "accomplished", Long.toString(o.accomplished.getTime()));
            }
            editor.putBoolean("Objectives" + "bgIsAvailableInNS", bgIsAvailableInNS);
            editor.putBoolean("Objectives" + "pumpStatusIsAvailableInNS", pumpStatusIsAvailableInNS);
            editor.putString("Objectives" + "manualEnacts", Integer.toString(manualEnacts));
            editor.apply();
            if (Config.logPrefsChange)
                log.debug("Objectives stored");
        }
    }

    void loadProgress() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        for (int num = 0; num < objectives.size(); num++) {
            Objective o = objectives.get(num);
            try {
                o.started = new Date(SafeParse.stringToLong(settings.getString("Objectives" + num + "started", "0")));
                o.accomplished = new Date(SafeParse.stringToLong(settings.getString("Objectives" + num + "accomplished", "0")));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        bgIsAvailableInNS = settings.getBoolean("Objectives" + "bgIsAvailableInNS", false);
        pumpStatusIsAvailableInNS = settings.getBoolean("Objectives" + "pumpStatusIsAvailableInNS", false);
        try {
            manualEnacts = SafeParse.stringToInt(settings.getString("Objectives" + "manualEnacts", "0"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Config.logPrefsChange)
            log.debug("Objectives loaded");
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
        if (objectives.get(4).started.getTime() > 0 || objectives.get(2).accomplished.getTime() == 0)
            return maxIob;
        else {
            if (Config.logConstraintsChanges)
                log.debug("Limiting maxIOB " + maxIob + " to " + 0 + "U");
            return 0d;
        }
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
