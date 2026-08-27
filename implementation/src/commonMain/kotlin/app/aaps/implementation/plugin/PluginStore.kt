package app.aaps.implementation.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.reflect.KClass

@ContributesBinding(AppScope::class, binding = binding<ActivePlugin>())
@SingleIn(AppScope::class)
class PluginStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val pumpWithConcentration: () -> PumpWithConcentration,
    // A factory, not an eager Set: a PermissionProvider (e.g. AutomationRuntime) transitively depends
    // on ActivePlugin (= this PluginStore), so asking for the set here would close the cycle. Was
    // dagger.Lazy; a plain lambda defers the same way without naming Dagger.
) : ActivePlugin {


    lateinit var plugins: List<PluginBase>


    private var activeBgSourceStore: BgSource? = null
    private var activePumpStore: Pump? = null
    private var activeAPSStore: APS? = null
    private var activeSensitivityStore: Sensitivity? = null
    private var activeSmoothingStore: Smoothing? = null
    private var activeCalibrationStore: Calibration? = null

    private fun getDefaultPlugin(type: PluginType): PluginBase {
        for (p in plugins)
            if (p.getType() == type && p.isDefault()) return p
        throw IllegalStateException("Default plugin not found")
    }

    override fun getSpecificPluginsList(type: PluginType): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.getType() == type) newList.add(p)
        }
        return newList
    }

    override fun beforeImport() {
        plugins.forEach {
            if (it is PluginBaseWithPreferences) it.beforeImport()
        }
    }

    override fun afterImport() {
        plugins.forEach {
            if (it is PluginBaseWithPreferences) it.afterImport()
        }
    }

    /**
     * True when the caller asked for something every plugin would match, which is not a question
     * about plugins at all.
     *
     * This was `interfaceClass.java.isAssignableFrom(ConfigBuilder::class.java)`, which is JVM
     * reflection. It needs no reflection: `ConfigBuilder` declares no supertypes, so the only
     * classes it is assignable to are itself and `Any`. Naming those two directly says the same
     * thing on every platform, and `PluginStoreInterfaceLookupTest` pins both cases.
     */
    private fun asksForTheBuilder(interfaceClass: KClass<*>): Boolean =
        interfaceClass == ConfigBuilder::class || interfaceClass == Any::class

    override fun getSpecificPluginsListByInterface(interfaceClass: KClass<*>): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (!asksForTheBuilder(interfaceClass) && interfaceClass.isInstance(p)) newList.add(p)
        }
        return newList
    }

    override fun getSpecificPluginsVisibleInList(type: PluginType): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.getType() == type) if (p.showInList(type)) newList.add(p)
        }
        return newList
    }

    override fun verifySelectionInCategories() {

        // PluginType.APS
        var pluginsInCategory = getSpecificPluginsList(PluginType.APS)
        activeAPSStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.APS) as APS?
        if (activeAPSStore == null) {
            activeAPSStore = getDefaultPlugin(PluginType.APS) as APS
            (activeAPSStore as PluginBase).setPluginEnabled(PluginType.APS, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting APSInterface")
        }

        // PluginType.SENSITIVITY
        pluginsInCategory = getSpecificPluginsList(PluginType.SENSITIVITY)
        activeSensitivityStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY) as Sensitivity?
        if (activeSensitivityStore == null) {
            activeSensitivityStore = getDefaultPlugin(PluginType.SENSITIVITY) as Sensitivity
            (activeSensitivityStore as PluginBase).setPluginEnabled(PluginType.SENSITIVITY, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SensitivityInterface")
        }
        activeSensitivityStore = fallbackIfNotVisible(activeSensitivityStore as PluginBase, PluginType.SENSITIVITY) as Sensitivity

        // PluginType.SMOOTHING
        pluginsInCategory = getSpecificPluginsList(PluginType.SMOOTHING)
        activeSmoothingStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SMOOTHING) as Smoothing?
        if (activeSmoothingStore == null) {
            activeSmoothingStore = getDefaultPlugin(PluginType.SMOOTHING) as Smoothing
            (activeSmoothingStore as PluginBase).setPluginEnabled(PluginType.SMOOTHING, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SmoothingInterface")
        }

        // PluginType.CALIBRATION
        pluginsInCategory = getSpecificPluginsList(PluginType.CALIBRATION)
        activeCalibrationStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.CALIBRATION) as Calibration?
        if (activeCalibrationStore == null) {
            activeCalibrationStore = getDefaultPlugin(PluginType.CALIBRATION) as Calibration
            (activeCalibrationStore as PluginBase).setPluginEnabled(PluginType.CALIBRATION, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting CalibrationInterface")
        }

        // PluginType.BGSOURCE
        pluginsInCategory = getSpecificPluginsList(PluginType.BGSOURCE)
        activeBgSourceStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.BGSOURCE) as BgSource?
        if (activeBgSourceStore == null) {
            activeBgSourceStore = getDefaultPlugin(PluginType.BGSOURCE) as BgSource
            (activeBgSourceStore as PluginBase).setPluginEnabled(PluginType.BGSOURCE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting BgInterface")
        }

        // PluginType.PUMP
        pluginsInCategory = getSpecificPluginsList(PluginType.PUMP)
        activePumpStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP) as Pump?
        if (activePumpStore == null) {
            activePumpStore = getDefaultPlugin(PluginType.PUMP) as Pump
            (activePumpStore as PluginBase).setPluginEnabled(PluginType.PUMP, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
    }

    /**
     * If the active plugin is no longer visible in its category (e.g., sensitivity plugin
     * incompatible with the current APS algorithm), disable it and fall back to the default.
     *
     * Framework plugins declared `alwaysEnabled` are exempt — they use `showInList { false }`
     * to hide from the UI list but must stay functional regardless.
     */
    private fun fallbackIfNotVisible(active: PluginBase, type: PluginType): PluginBase {
        if (active.pluginDescription.alwaysEnabled) return active
        if (!active.showInList(type)) {
            active.setPluginEnabled(type, false)
            val default = getDefaultPlugin(type)
            default.setPluginEnabled(type, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Falling back ${type.name} from ${active.name} to ${default.name}")
            return default
        }
        return active
    }

    private fun getTheOneEnabledInArray(pluginsInCategory: ArrayList<PluginBase>, type: PluginType): PluginBase? {
        var found: PluginBase? = null
        for (p in pluginsInCategory) {
            if (p.isEnabled(type) && found == null) {
                found = p
            } else if (p.isEnabled(type)) {
                // set others disabled
                p.setPluginEnabled(type, false)
            }
        }
        return found
    }

    // ***** Interface *****

    override val activeBgSource: BgSource
        get() = activeBgSourceStore ?: checkNotNull(activeBgSourceStore) { "No bg source selected" }

    override val activeAPS: APS?
        get() = activeAPSStore

    override val activePump: PumpWithConcentration
        get() = pumpWithConcentration()

    /**
     * Points to real pump plugin selected in ConfigBuilder
     * For use only from [app.aaps.implementation.pump.PumpWithConcentrationImpl]
     */
    override val activePumpInternal: Pump
        get() = activePumpStore
        // Following line can be used only during initialization
            ?: getTheOneEnabledInArray(getSpecificPluginsList(PluginType.PUMP), PluginType.PUMP) as Pump?
            ?: checkNotNull(activePumpStore) { "No pump selected" }

    override val activeSensitivity: Sensitivity
        get() = activeSensitivityStore ?: checkNotNull(activeSensitivityStore) { "No sensitivity selected" }

    override val activeSmoothing: Smoothing
        get() = activeSmoothingStore ?: checkNotNull(activeSmoothingStore) { "No smoothing selected" }

    override val activeCalibration: Calibration
        get() = activeCalibrationStore ?: checkNotNull(activeCalibrationStore) { "No calibration selected" }

    override val activeSafety: Safety
        get() = getSpecificPluginsListByInterface(Safety::class).first() as Safety

    override val activeIobCobCalculator: IobCobCalculator
        get() = getSpecificPluginsListByInterface(IobCobCalculator::class).first() as IobCobCalculator
    override val activeObjectives: Objectives?
        get() = getSpecificPluginsListByInterface(Objectives::class).firstOrNull() as Objectives?

    @Suppress("UNCHECKED_CAST")
    override val firstActiveSync: Sync?
        get() = (getSpecificPluginsListByInterface(Sync::class) as ArrayList<Sync>).firstOrNull { it.connected }

    @Suppress("UNCHECKED_CAST")
    override val activeSyncs: ArrayList<Sync>
        get() = getSpecificPluginsListByInterface(Sync::class) as ArrayList<Sync>

    override fun getPluginsList(): ArrayList<PluginBase> = ArrayList(plugins)

}