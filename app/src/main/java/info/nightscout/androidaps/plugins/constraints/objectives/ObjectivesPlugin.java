package info.nightscout.androidaps.plugins.constraints.objectives;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.constraints.objectives.events.EventObjectivesSaved;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective1;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective2;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective3;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective4;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective5;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective6;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective7;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective8;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class ObjectivesPlugin extends PluginBase implements ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(L.CONSTRAINTS);

    private static ObjectivesPlugin objectivesPlugin;

    public List<Objective> objectives = new ArrayList<>();
    public boolean bgIsAvailableInNS = false;
    public boolean pumpStatusIsAvailableInNS = false;
    public Integer manualEnacts = 0;

    public static final int FIRST_OBJECTIVE = 0;
    public static final int CLOSED_LOOP_OBJECTIVE = 3;
    public static final int MAXIOB_ZERO_OBJECTIVE = 3;
    public static final int AUTOSENS_OBJECTIVE = 5;
    public static final int AMA_OBJECTIVE = 6;
    public static final int SMB_OBJECTIVE = 7;

    public static ObjectivesPlugin getPlugin() {
        if (objectivesPlugin == null) {
            objectivesPlugin = new ObjectivesPlugin();
        }
        return objectivesPlugin;
    }

    private ObjectivesPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .fragmentClass(ObjectivesFragment.class.getName())
                .alwaysEnabled(!Config.NSCLIENT)
                .showInList(!Config.NSCLIENT)
                .pluginName(R.string.objectives)
                .shortName(R.string.objectives_shortname)
                .description(R.string.description_objectives)
        );
        setupObjectives();
        loadProgress();
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }

    private void setupObjectives() {
        objectives.add(new Objective1());
        objectives.add(new Objective2());
        objectives.add(new Objective3());
        objectives.add(new Objective4());
        objectives.add(new Objective5());
        objectives.add(new Objective6());
        objectives.add(new Objective7());
        objectives.add(new Objective8());
    }

    public void reset() {
        for (Objective objective : objectives) {
            objective.setStartedOn(null);
            objective.setAccomplishedOn(null);
        }
        bgIsAvailableInNS = false;
        pumpStatusIsAvailableInNS = false;
        manualEnacts = 0;
        saveProgress();
    }

    public void saveProgress() {
        SP.putBoolean("Objectives" + "bgIsAvailableInNS", bgIsAvailableInNS);
        SP.putBoolean("Objectives" + "pumpStatusIsAvailableInNS", pumpStatusIsAvailableInNS);
        SP.putString("Objectives" + "manualEnacts", Integer.toString(manualEnacts));
        if (L.isEnabled(L.CONSTRAINTS))
            log.debug("Objectives stored");
        MainApp.bus().post(new EventObjectivesSaved());
    }

    private void loadProgress() {
        bgIsAvailableInNS = SP.getBoolean("Objectives" + "bgIsAvailableInNS", false);
        pumpStatusIsAvailableInNS = SP.getBoolean("Objectives" + "pumpStatusIsAvailableInNS", false);
        try {
            manualEnacts = SP.getInt("Objectives" + "manualEnacts", 0);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        if (L.isEnabled(L.CONSTRAINTS))
            log.debug("Objectives loaded");
    }

    public List<Objective> getObjectives() {
        return objectives;
    }

    /**
     * Constraints interface
     **/
    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {
        if (!objectives.get(FIRST_OBJECTIVE).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), FIRST_OBJECTIVE + 1), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        if (!objectives.get(CLOSED_LOOP_OBJECTIVE).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), CLOSED_LOOP_OBJECTIVE + 1), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value) {
        if (!objectives.get(AUTOSENS_OBJECTIVE).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), AUTOSENS_OBJECTIVE + 1), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value) {
        if (!objectives.get(AMA_OBJECTIVE).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), AMA_OBJECTIVE + 1), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value) {
        if (!objectives.get(SMB_OBJECTIVE).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), SMB_OBJECTIVE + 1), this);
        return value;
    }

    @Override
    public Constraint<Double> applyMaxIOBConstraints(Constraint<Double> maxIob) {
        if (objectives.get(MAXIOB_ZERO_OBJECTIVE).isStarted() && !objectives.get(MAXIOB_ZERO_OBJECTIVE).isAccomplished())
            maxIob.set(0d, String.format(MainApp.gs(R.string.objectivenotfinished), MAXIOB_ZERO_OBJECTIVE + 1), this);
        return maxIob;
    }

}
