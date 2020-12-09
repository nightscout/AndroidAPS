package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class Objective0 extends Objective {
    @Inject SP sp;
    @Inject ActivePluginProvider activePlugin;
    @Inject VirtualPumpPlugin virtualPumpPlugin;
    @Inject TreatmentsPlugin treatmentsPlugin;
    @Inject LoopPlugin loopPlugin;
    @Inject NSClientPlugin nsClientPlugin;
    @Inject IobCobCalculatorPlugin iobCobCalculatorPlugin;

    public Objective0(HasAndroidInjector injector) {
        super(injector, "config", R.string.objectives_0_objective, R.string.objectives_0_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new Task(R.string.objectives_bgavailableinns) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_ObjectivesbgIsAvailableInNS, false);
            }
        });
        tasks.add(new Task(R.string.nsclienthaswritepermission) {
            @Override
            public boolean isCompleted() {
                return nsClientPlugin.hasWritePermission();
            }
        });
        tasks.add(new Task(R.string.virtualpump_uploadstatus_title) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_virtualpump_uploadstatus, false);
            }

            @Override
            public boolean shouldBeIgnored() {
                return !virtualPumpPlugin.isEnabled(PluginType.PUMP);
            }
        });
        tasks.add(new Task(R.string.objectives_pumpstatusavailableinns) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, false);
            }
        });
        tasks.add(new Task(R.string.hasbgdata) {
            @Override
            public boolean isCompleted() {
                return iobCobCalculatorPlugin.lastBg() != null;
            }
        });
        tasks.add(new Task(R.string.loopenabled) {
            @Override
            public boolean isCompleted() {
                return loopPlugin.isEnabled(PluginType.LOOP);
            }
        });
        tasks.add(new Task(R.string.apsselected) {
            @Override
            public boolean isCompleted() {
                APSInterface usedAPS = activePlugin.getActiveAPS();
                return ((PluginBase) usedAPS).isEnabled(PluginType.APS);
            }
        });
        tasks.add(new Task(R.string.activate_profile) {
            @Override
            public boolean isCompleted() {
                return treatmentsPlugin.getProfileSwitchFromHistory(DateUtil.now()) != null;
            }
        });
    }
}
