package info.nightscout.androidaps.plugins.ConstraintsObjectives;

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
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class ObjectivesPlugin extends PluginBase implements ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ObjectivesPlugin.class);

    private static ObjectivesPlugin objectivesPlugin;

    public static ObjectivesPlugin getPlugin() {
        if (objectivesPlugin == null) {
            objectivesPlugin = new ObjectivesPlugin();
        }
        return objectivesPlugin;
    }

    public static List<Objective> objectives;

    private ObjectivesPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .fragmentClass(ObjectivesFragment.class.getName())
                .alwaysEnabled(!Config.NSCLIENT && !Config.G5UPLOADER)
                .showInList(!Config.NSCLIENT && !Config.G5UPLOADER)
                .pluginName(R.string.objectives)
                .shortName(R.string.objectives_shortname)
        );
        initializeData();
        loadProgress();
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
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

        public void setStarted(Date started) {
            this.started = started;
        }

        boolean isStarted() {
            return started.getTime() > 0;
        }

        boolean isFinished() {
            return accomplished.getTime() != 0;
        }
    }

    // Objective 0
    public static boolean bgIsAvailableInNS = false;
    public static boolean pumpStatusIsAvailableInNS = false;
    // Objective 1
    public static Integer manualEnacts = 0;
    private static final Integer manualEnactsNeeded = 20;

    class RequirementResult {
        boolean done = false;
        String comment = "";

        RequirementResult(boolean done, String comment) {
            this.done = done;
            this.comment = comment;
        }
    }

    private String yesOrNo(boolean yes) {
        if (yes) return "☺";
        else return "---";
    }

    RequirementResult requirementsMet(Integer objNum) {
        switch (objNum) {
            case 0:
                boolean isVirtualPump = VirtualPumpPlugin.getPlugin().isEnabled(PluginType.PUMP);
                boolean vpUploadEnabled = SP.getBoolean("virtualpump_uploadstatus", false);
                boolean vpUploadNeeded = !isVirtualPump || vpUploadEnabled;
                boolean hasBGData = DatabaseHelper.lastBg() != null;

                boolean apsEnabled = false;
                APSInterface usedAPS = ConfigBuilderPlugin.getActiveAPS();
                if (usedAPS != null && ((PluginBase) usedAPS).isEnabled(PluginType.APS))
                    apsEnabled = true;

                boolean profileSwitchExists = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(DateUtil.now()) != null;

                return new RequirementResult(hasBGData && bgIsAvailableInNS && pumpStatusIsAvailableInNS && NSClientPlugin.getPlugin().hasWritePermission() && LoopPlugin.getPlugin().isEnabled(PluginType.LOOP) && apsEnabled && vpUploadNeeded && profileSwitchExists,
                        MainApp.gs(R.string.objectives_bgavailableinns) + ": " + yesOrNo(bgIsAvailableInNS)
                                + "\n" + MainApp.gs(R.string.nsclienthaswritepermission) + ": " + yesOrNo(NSClientPlugin.getPlugin().hasWritePermission())
                                + (isVirtualPump ? "\n" + MainApp.gs(R.string.virtualpump_uploadstatus_title) + ": " + yesOrNo(vpUploadEnabled) : "")
                                + "\n" + MainApp.gs(R.string.objectives_pumpstatusavailableinns) + ": " + yesOrNo(pumpStatusIsAvailableInNS)
                                + "\n" + MainApp.gs(R.string.hasbgdata) + ": " + yesOrNo(hasBGData)
                                + "\n" + MainApp.gs(R.string.loopenabled) + ": " + yesOrNo(LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))
                                + "\n" + MainApp.gs(R.string.apsselected) + ": " + yesOrNo(apsEnabled)
                                + "\n" + MainApp.gs(R.string.activate_profile) + ": " + yesOrNo(profileSwitchExists)
                );
            case 1:
                return new RequirementResult(manualEnacts >= manualEnactsNeeded,
                        MainApp.gs(R.string.objectives_manualenacts) + ": " + manualEnacts + "/" + manualEnactsNeeded);
            case 2:
                return new RequirementResult(true, "");
            case 3:
                Constraint<Boolean> closedLoopEnabled = new Constraint<>(true);
                SafetyPlugin.getPlugin().isClosedLoopAllowed(closedLoopEnabled);
                return new RequirementResult(closedLoopEnabled.value(), MainApp.gs(R.string.closedmodeenabled) + ": " + yesOrNo(closedLoopEnabled.value()));
            case 4:
                double maxIOB = MainApp.getConstraintChecker().getMaxIOBAllowed().value();
                boolean maxIobSet = maxIOB > 0;
                return new RequirementResult(maxIobSet, MainApp.gs(R.string.maxiobset) + ": " + yesOrNo(maxIobSet));
            default:
                return new RequirementResult(true, "");
        }
    }


    void initializeData() {
        bgIsAvailableInNS = false;
        pumpStatusIsAvailableInNS = false;
        manualEnacts = 0;

        objectives = new ArrayList<>();
        objectives.add(new Objective(0,
                MainApp.gs(R.string.objectives_0_objective),
                MainApp.gs(R.string.objectives_0_gate),
                new Date(0),
                0, // 0 day
                new Date(0)));
        objectives.add(new Objective(1,
                MainApp.gs(R.string.objectives_1_objective),
                MainApp.gs(R.string.objectives_1_gate),
                new Date(0),
                7, // 7 days
                new Date(0)));
        objectives.add(new Objective(2,
                MainApp.gs(R.string.objectives_2_objective),
                MainApp.gs(R.string.objectives_2_gate),
                new Date(0),
                0, // 0 days
                new Date(0)));
        objectives.add(new Objective(3,
                MainApp.gs(R.string.objectives_3_objective),
                MainApp.gs(R.string.objectives_3_gate),
                new Date(0),
                5, // 5 days
                new Date(0)));
        objectives.add(new Objective(4,
                MainApp.gs(R.string.objectives_4_objective),
                MainApp.gs(R.string.objectives_4_gate),
                new Date(0),
                1,
                new Date(0)));
        objectives.add(new Objective(5,
                MainApp.gs(R.string.objectives_5_objective),
                MainApp.gs(R.string.objectives_5_gate),
                new Date(0),
                7,
                new Date(0)));
        objectives.add(new Objective(6,
                MainApp.gs(R.string.objectives_6_objective),
                "",
                new Date(0),
                28,
                new Date(0)));
        objectives.add(new Objective(7,
                MainApp.gs(R.string.objectives_7_objective),
                "",
                new Date(0),
                28,
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

    private void loadProgress() {
        for (int num = 0; num < objectives.size(); num++) {
            Objective o = objectives.get(num);
            try {
                o.started = new Date(SP.getLong("Objectives" + num + "started", 0L));
                o.accomplished = new Date(SP.getLong("Objectives" + num + "accomplished", 0L));
            } catch (Exception e) {
                log.error("Unhandled exception", e);
            }
        }
        bgIsAvailableInNS = SP.getBoolean("Objectives" + "bgIsAvailableInNS", false);
        pumpStatusIsAvailableInNS = SP.getBoolean("Objectives" + "pumpStatusIsAvailableInNS", false);
        try {
            manualEnacts = SP.getInt("Objectives" + "manualEnacts", 0);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        if (Config.logPrefsChange)
            log.debug("Objectives loaded");
    }

    /**
     * Constraints interface
     **/
    @Override
    public Constraint<Boolean> isLoopInvokationAllowed(Constraint<Boolean> value) {
        if (!objectives.get(0).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), 1), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        if (!objectives.get(3).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), 4), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value) {
        if (!objectives.get(5).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), 6), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value) {
        if (!objectives.get(6).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), 7), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value) {
        if (!objectives.get(7).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), 8), this);
        return value;
    }

    @Override
    public Constraint<Double> applyMaxIOBConstraints(Constraint<Double> maxIob) {
        if (objectives.get(3).isStarted() && !objectives.get(3).isFinished())
            maxIob.set(0d, String.format(MainApp.gs(R.string.objectivenotfinished), 4), this);
        return maxIob;
    }

}
