package info.nightscout.androidaps.plugins.constraints.objectives;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
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
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective0;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective1;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective2;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective3;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective4;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective5;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective6;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective7;
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective8;
import info.nightscout.androidaps.utils.DateUtil;
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
    public static final int USAGE_OBJECTIVE = 1;
    public static final int OPENLOOP_OBJECTIVE = 2;
    public static final int MAXBASAL_OBJECTIVE = 3;
    public static final int MAXIOB_ZERO_CL_OBJECTIVE = 4;
    public static final int MAXIOB_OBJECTIVE = 5;
    public static final int AUTOSENS_OBJECTIVE = 6;
    public static final int AMA_OBJECTIVE = 7;
    public static final int SMB_OBJECTIVE = 8;

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
        convertSP();
        setupObjectives();
        loadProgress();
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }

    // convert 2.3 SP  version
    private void convertSP() {
        doConvertSP(0, "config");
        doConvertSP(1, "openloop");
        doConvertSP(2, "maxbasal");
        doConvertSP(3, "maxiobzero");
        doConvertSP(4, "maxiob");
        doConvertSP(5, "autosens");
        doConvertSP(6, "ama");
        doConvertSP(7, "smb");
    }

    private void doConvertSP(int number, String name) {
        if (!SP.contains("Objectives_" + name + "_started")) {
            SP.putLong("Objectives_" + name + "_started", SP.getLong("Objectives" + number + "accomplished", 0L));
            SP.putLong("Objectives_" + name + "_accomplished", SP.getLong("Objectives" + number + "accomplished", 0L));
        }
    }

    private void setupObjectives() {
        objectives.add(new Objective0());
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
            objective.setStartedOn(0);
            objective.setAccomplishedOn(0);
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

    public void completeObjectives(String request) {
        String url = SP.getString(R.string.key_nsclientinternal_url, "").toLowerCase();
        if (!url.endsWith("\"")) url = url + "\"";
        String hashNS = Hashing.sha1().hashString(url + BuildConfig.APPLICATION_ID, Charsets.UTF_8).toString();
        if (request.equalsIgnoreCase(hashNS.substring(0, 9))) {
            SP.putLong("Objectives_" + "openloop" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "openloop" + "_accomplished", DateUtil.now());
            SP.putLong("Objectives_" + "maxbasal" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "maxbasal" + "_accomplished", DateUtil.now());
            SP.putLong("Objectives_" + "maxiobzero" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "maxiobzero" + "_accomplished", DateUtil.now());
            SP.putLong("Objectives_" + "maxiob" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "maxiob" + "_accomplished", DateUtil.now());
            SP.putLong("Objectives_" + "autosens" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "autosens" + "_accomplished", DateUtil.now());
            SP.putLong("Objectives_" + "ama" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "ama" + "_accomplished", DateUtil.now());
            SP.putLong("Objectives_" + "smb" + "_started", DateUtil.now());
            SP.putLong("Objectives_" + "smb" + "_accomplished", DateUtil.now());
        }
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
        if (!objectives.get(MAXIOB_ZERO_CL_OBJECTIVE).isStarted())
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), MAXIOB_ZERO_CL_OBJECTIVE + 1), this);
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
        if (objectives.get(MAXIOB_ZERO_CL_OBJECTIVE).isStarted() && !objectives.get(MAXIOB_ZERO_CL_OBJECTIVE).isAccomplished())
            maxIob.set(0d, String.format(MainApp.gs(R.string.objectivenotfinished), MAXIOB_ZERO_CL_OBJECTIVE + 1), this);
        return maxIob;
    }

}
