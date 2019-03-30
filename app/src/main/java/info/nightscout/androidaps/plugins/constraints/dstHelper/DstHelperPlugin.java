package info.nightscout.androidaps.plugins.constraints.dstHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;

public class DstHelperPlugin extends PluginBase implements ConstraintsInterface {
    public static final int DISABLE_TIMEFRAME_HOURS = -3;
    public static final int WARN_PRIOR_TIMEFRAME_HOURS = 24;
    private static Logger log = LoggerFactory.getLogger(L.CONSTRAINTS);

    static DstHelperPlugin plugin = null;

    public static DstHelperPlugin getPlugin() {
        if (plugin == null)
            plugin = new DstHelperPlugin();
        return plugin;
    }

    public DstHelperPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.dst_plugin_name)
        );
    }

    public static boolean wasDST(Calendar now) {
        Calendar ago = (Calendar) now.clone();
        ago.add(Calendar.HOUR, DISABLE_TIMEFRAME_HOURS);
        return now.get(Calendar.DST_OFFSET) != ago.get(Calendar.DST_OFFSET);
    }

    public static boolean willBeDST(Calendar now) {
        Calendar ago = (Calendar) now.clone();
        ago.add(Calendar.HOUR, WARN_PRIOR_TIMEFRAME_HOURS);
        return now.get(Calendar.DST_OFFSET) != ago.get(Calendar.DST_OFFSET);
    }

    //Return false if time to DST change happened in the last 3 hours.
    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {

        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump == null || pump.canHandleDST()) {
            log.debug("Pump can handle DST");
            return value;
        }

        Calendar cal = Calendar.getInstance();

        if (willBeDST(cal)) {
            warnUser(Notification.DST_IN_24H, MainApp.gs(R.string.dst_in_24h_warning));
        }

        if (!value.value()) {
            log.debug("Already not allowed - don't check further");
            return value;
        }

        if (wasDST(cal)) {
            LoopPlugin loopPlugin = LoopPlugin.getPlugin();
            if (!loopPlugin.isSuspended()) {
                warnUser(Notification.DST_LOOP_DISABLED, MainApp.gs(R.string.dst_loop_disabled_warning));
            } else {
                log.debug("Loop already suspended");
            }
            value.set(false, "DST in last 3 hours.", this);
        }
        return value;
    }

    private void warnUser(int id, String warningText) {
        Notification notification = new Notification(id, warningText, Notification.LOW);
        MainApp.bus().post(new EventNewNotification(notification));
    }
}