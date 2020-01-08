package info.nightscout.androidaps.interfaces

import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventConfigBuilderChange
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.logging.L.isEnabled
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.EventConfigBuilderUpdateGui
import info.nightscout.androidaps.utils.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.SP
import org.slf4j.LoggerFactory

/**
 * Created by mike on 09.06.2016.
 */
abstract class PluginBase(val pluginDescription: PluginDescription, val rxBus: RxBusWrapper, val aapsLogger: AAPSLogger) {

    companion object {
        private val log = LoggerFactory.getLogger(L.CORE)
    }

    enum class State {
        NOT_INITIALIZED, ENABLED, DISABLED
    }

    private var state = State.NOT_INITIALIZED
    private var fragmentVisible = false
    // Specific plugin with more Interfaces
    protected var isProfileInterfaceEnabled = false

    // Default always calls invoke
    // Plugins that have special constraints if they get switched to may override this method
    open fun switchAllowed(newState: Boolean, activity: FragmentActivity?, type: PluginType) {
        performPluginSwitch(newState, type)
    }

    protected fun confirmPumpPluginActivation(newState: Boolean, activity: FragmentActivity?, type: PluginType) {
        if (type == PluginType.PUMP) {
            val allowHardwarePump = SP.getBoolean("allow_hardware_pump", false)
            if (allowHardwarePump || activity == null) {
                performPluginSwitch(newState, type)
            } else {
                showConfirmation(activity, MainApp.gs(R.string.allow_hardware_pump_text), Runnable {
                    performPluginSwitch(newState, type)
                    SP.putBoolean("allow_hardware_pump", true)
                    if (isEnabled(L.PUMP)) log.debug("First time HW pump allowed!")
                }, Runnable {
                    RxBus.INSTANCE.send(EventConfigBuilderUpdateGui())
                    if (isEnabled(L.PUMP)) log.debug("User does not allow switching to HW pump!")
                })
            }
        } else {
            performPluginSwitch(newState, type)
        }
    }

    private fun performPluginSwitch(enabled: Boolean, type: PluginType) {
        setPluginEnabled(type, enabled)
        setFragmentVisible(type, enabled)
        ConfigBuilderPlugin.getPlugin().processOnEnabledCategoryChanged(this, type)
        ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxEnabled")
        RxBus.INSTANCE.send(EventRebuildTabs())
        RxBus.INSTANCE.send(EventConfigBuilderChange())
        RxBus.INSTANCE.send(EventConfigBuilderUpdateGui())
        ConfigBuilderPlugin.getPlugin().logPluginStatus()
    }

    open val name: String
        get() = if (pluginDescription.pluginName == -1) "UNKNOWN" else MainApp.gs(pluginDescription.pluginName)

    //only if translation exists
    // use long name as fallback
    val nameShort: String
        get() {
            if (pluginDescription.shortName == -1) return name
            val translatedName = MainApp.gs(pluginDescription.shortName)
            return if (!translatedName.trim { it <= ' ' }.isEmpty()) translatedName else name
            // use long name as fallback
        }

    val description: String?
        get() = if (pluginDescription.description == -1) null else MainApp.gs(pluginDescription.description)

    fun getType(): PluginType = pluginDescription.mainType

    open val preferencesId: Int
        get() = pluginDescription.preferencesId

    fun isEnabled() = isEnabled(pluginDescription.mainType)

    fun isEnabled(type: PluginType): Boolean {
        if (pluginDescription.alwaysEnabled && type == pluginDescription.mainType) return true
        if (pluginDescription.mainType == PluginType.CONSTRAINTS && type == PluginType.CONSTRAINTS) return true
        if (type == pluginDescription.mainType) return state == State.ENABLED && specialEnableCondition()
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.PUMP && isEnabled(PluginType.PUMP)) return true
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.APS && isEnabled(PluginType.APS)) return true
        return if (type == PluginType.PROFILE && pluginDescription.mainType == PluginType.PUMP) isProfileInterfaceEnabled else false
    }

    fun hasFragment(): Boolean {
        return pluginDescription.fragmentClass != null
    }

    /**
     * So far plugin can have it's main type + ConstraintInterface + ProfileInterface
     * ConstraintInterface is enabled if main plugin is enabled
     * ProfileInterface can be enabled only  if main iterface is enable
     */
    fun setPluginEnabled(type: PluginType, newState: Boolean) {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    if (isEnabled(L.CORE)) log.debug("Starting: $name")
                    onStart()
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    onStop()
                    if (isEnabled(L.CORE)) log.debug("Stopping: $name")
                }
            }
        } else if (type == PluginType.PROFILE) {
            isProfileInterfaceEnabled = newState
        }
    }

    fun setFragmentVisible(type: PluginType, fragmentVisible: Boolean) {
        if (type == pluginDescription.mainType) {
            this.fragmentVisible = fragmentVisible && specialEnableCondition()
        }
    }

    fun isFragmentVisible(): Boolean {
        if (pluginDescription.alwaysVisible) return true
        return if (pluginDescription.neverVisible) false else fragmentVisible
    }

    fun showInList(type: PluginType): Boolean {
        if (pluginDescription.mainType == type) return pluginDescription.showInList && specialShowInListCondition()
        return if (type == PluginType.PROFILE && pluginDescription.mainType == PluginType.PUMP) isEnabled(PluginType.PUMP) else false
    }

    open fun specialEnableCondition(): Boolean {
        return true
    }

    open fun specialShowInListCondition(): Boolean {
        return true
    }

    protected open fun onStart() {
        if (getType() == PluginType.PUMP) {
            Thread(Runnable {
                SystemClock.sleep(3000)
                val commandQueue = ConfigBuilderPlugin.getPlugin().commandQueue
                commandQueue?.readStatus("Pump driver changed.", null)
            }).start()
        }
    }

    protected open fun onStop() {}
    protected open fun onStateChange(type: PluginType?, oldState: State?, newState: State?) {}
    open fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {}
    open fun updatePreferenceSummary(pref: Preference) {}
}