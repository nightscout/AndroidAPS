package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

public class Objective0 extends Objective {

    public Objective0() {
        super("config", R.string.objectives_0_objective, R.string.objectives_0_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new Task(R.string.objectives_bgavailableinns) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_ObjectivesbgIsAvailableInNS, false);
            }
        });
        tasks.add(new Task(R.string.nsclienthaswritepermission) {
            @Override
            public boolean isCompleted() {
                return NSClientPlugin.getPlugin().hasWritePermission();
            }
        });
        tasks.add(new Task(R.string.virtualpump_uploadstatus_title) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean("virtualpump_uploadstatus", false);
            }

            @Override
            public boolean shouldBeIgnored() {
                return !VirtualPumpPlugin.getPlugin().isEnabled(PluginType.PUMP);
            }
        });
        tasks.add(new Task(R.string.objectives_pumpstatusavailableinns) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, false);
            }
        });
        tasks.add(new Task(R.string.hasbgdata) {
            @Override
            public boolean isCompleted() {
                return DatabaseHelper.lastBg() != null;
            }
        });
        tasks.add(new Task(R.string.loopenabled) {
            @Override
            public boolean isCompleted() {
                return LoopPlugin.getPlugin().isEnabled(PluginType.LOOP);
            }
        });
        tasks.add(new Task(R.string.apsselected) {
            @Override
            public boolean isCompleted() {
                APSInterface usedAPS = ConfigBuilderPlugin.getPlugin().getActiveAPS();
                if (usedAPS != null && ((PluginBase) usedAPS).isEnabled(PluginType.APS))
                    return true;
                return false;
            }
        });
        tasks.add(new Task(R.string.activate_profile) {
            @Override
            public boolean isCompleted() {
                return TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(DateUtil.now()) != null;
            }
        });
    }
}
