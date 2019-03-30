package info.nightscout.androidaps.interfaces;

import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.CommandQueue;

/**
 * Created by mike on 09.06.2016.
 */
public abstract class PluginBase {
    private static Logger log = LoggerFactory.getLogger(L.CORE);

    public enum State {
        NOT_INITIALIZED,
        ENABLED,
        DISABLED
    }

    private State state = State.NOT_INITIALIZED;
    private boolean isFragmentVisible = false;
    public PluginDescription pluginDescription;


    // Specific plugin with more Interfaces
    protected boolean isProfileInterfaceEnabled = false;

    public PluginBase(PluginDescription pluginDescription) {
        this.pluginDescription = pluginDescription;
    }

    // Default always calls invoke
    // Plugins that have special constraints if they get switched to may override this method
    public void switchAllowed(ConfigBuilderFragment.PluginViewHolder.PluginSwitcher pluginSwitcher, FragmentActivity activity) {
        pluginSwitcher.invoke();
    }

//    public PluginType getType() {
//        return mainType;
//    }

//    public String getFragmentClass() {
//        return fragmentClass;
//    }

    public String getName() {
        if (pluginDescription.pluginName == -1)
            return "UKNOWN";
        else
            return MainApp.gs(pluginDescription.pluginName);
    }

    public String getNameShort() {
        if (pluginDescription.shortName == -1)
            return getName();
        String name = MainApp.gs(pluginDescription.shortName);
        if (!name.trim().isEmpty()) //only if translation exists
            return name;
        // use long name as fallback
        return getName();
    }

    public String getDescription() {
        if (pluginDescription.description == -1) return null;
        else return MainApp.gs(pluginDescription.description);
    }

    public PluginType getType() {
        return pluginDescription.mainType;
    }

    public int getPreferencesId() {
        return pluginDescription.preferencesId;
    }

    public int getAdvancedPreferencesId() {
        return pluginDescription.advancedPreferencesId;
    }

    public boolean isEnabled(PluginType type) {
        if (pluginDescription.alwaysEnabled && type == pluginDescription.mainType)
            return true;
        if (pluginDescription.mainType == PluginType.CONSTRAINTS && type == PluginType.CONSTRAINTS)
            return true;
        if (type == pluginDescription.mainType)
            return state == State.ENABLED && specialEnableCondition();
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.PUMP && isEnabled(PluginType.PUMP))
            return true;
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.APS && isEnabled(PluginType.APS))
            return true;
        if (type == PluginType.PROFILE && pluginDescription.mainType == PluginType.PUMP)
            return isProfileInterfaceEnabled;
        return false;
    }

    public boolean hasFragment() {
        return pluginDescription.fragmentClass != null;
    }


    /**
     * So far plugin can have it's main type + ConstraintInterface + ProfileInterface
     * ConstraintInterface is enabled if main plugin is enabled
     * ProfileInterface can be enabled only  if main iterface is enable
     */

    public void setPluginEnabled(PluginType type, boolean newState) {
        if (type == pluginDescription.mainType) {
            if (newState == true) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED);
                    state = State.ENABLED;
                    if (L.isEnabled(L.CORE))
                        log.debug("Starting: " + getName());
                    onStart();
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED);
                    state = State.DISABLED;
                    onStop();
                    if (L.isEnabled(L.CORE))
                        log.debug("Stopping: " + getName());
                }
            }
        } else if (type == PluginType.PROFILE) {
            isProfileInterfaceEnabled = newState;
        }

    }

    public void setFragmentVisible(PluginType type, boolean fragmentVisible) {
        if (type == pluginDescription.mainType) {
            isFragmentVisible = fragmentVisible && specialEnableCondition();
        }
    }

    public boolean isFragmentVisible() {
        if (pluginDescription.alwayVisible)
            return true;
        if (pluginDescription.neverVisible)
            return false;
        return isFragmentVisible;
    }

    public boolean showInList(PluginType type) {
        if (pluginDescription.mainType == type)
            return pluginDescription.showInList && specialShowInListCondition();

        if (type == PluginType.PROFILE && pluginDescription.mainType == PluginType.PUMP)
            return isEnabled(PluginType.PUMP);
        return false;
    }

    public boolean specialEnableCondition() {
        return true;
    }

    public boolean specialShowInListCondition() {
        return true;
    }

    protected void onStart() {
        if (getType() == PluginType.PUMP) {
            new Thread(() -> {
                SystemClock.sleep(3000);
                CommandQueue commandQueue = ConfigBuilderPlugin.getPlugin().getCommandQueue();
                if (commandQueue != null)
                    commandQueue.readStatus("Pump driver changed.", null);
            }).start();
        }
    }

    protected void onStop() {
    }

    protected void onStateChange(PluginType type, State oldState, State newState) {
    }
}