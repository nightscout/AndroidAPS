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
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Job
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

@ContributesBinding(AppScope::class, binding = binding<ActivePlugin>())
@SingleIn(AppScope::class)
@Inject
class PluginStore(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val pumpWithConcentration: () -> PumpWithConcentration,
) : ActivePlugin {


    lateinit var plugins: List<PluginBase>


    /**
     * The elected plugin per category. Written by [verifySelectionInCategories], read from everywhere.
     *
     * [Volatile] because the write and the reads are on different threads and nothing else orders them:
     * the election runs on the import screen's dispatcher (and on the start-up scope), while the
     * accessors below are read by the loop, the queue, the UI and the wear handlers. Without it there is
     * no happens-before edge, so a reader has no guarantee of seeing the elected value at all, or of
     * seeing the two writes in [verifySelectionInCategories] in the order they were made.
     *
     * **This does NOT make the election atomic, and it is not meant to.** Each category is written
     * twice there - once with the result of `getTheOneEnabledInArray`, which can be null, and again with
     * the default if it was. A reader landing between those two writes still sees null and still falls
     * through to the assertion. [Volatile] only narrows that window from "unbounded, by the memory
     * model" to the handful of instructions it appears to be in the source. Closing it properly means
     * not reading plugin state while the election runs - see `Config.appInitialized` and the
     * reconfiguring window - not holding a value over, which would hand the reader a stale disabled
     * plugin instead.
     */
    @Volatile private var activeBgSourceStore: BgSource? = null
    @Volatile private var activePumpStore: Pump? = null
    @Volatile private var activeAPSStore: APS? = null
    @Volatile private var activeSensitivityStore: Sensitivity? = null
    @Volatile private var activeSmoothingStore: Smoothing? = null
    @Volatile private var activeCalibrationStore: Calibration? = null

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

    override fun verifySelectionInCategories(): List<Job> {
        // Electing a plugin enables it, and enabling only schedules onStart on the plugin scope. These
        // jobs used to be dropped here, so a caller could not wait for the plugins it had just elected.
        val jobs = mutableListOf<Job>()

        // PluginType.APS
        var pluginsInCategory = getSpecificPluginsList(PluginType.APS)
        activeAPSStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.APS, jobs) as APS?
        if (activeAPSStore == null) {
            activeAPSStore = getDefaultPlugin(PluginType.APS) as APS
            (activeAPSStore as PluginBase).setPluginEnabled(PluginType.APS, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting APSInterface")
        }

        // PluginType.SENSITIVITY
        pluginsInCategory = getSpecificPluginsList(PluginType.SENSITIVITY)
        activeSensitivityStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY, jobs) as Sensitivity?
        if (activeSensitivityStore == null) {
            activeSensitivityStore = getDefaultPlugin(PluginType.SENSITIVITY) as Sensitivity
            (activeSensitivityStore as PluginBase).setPluginEnabled(PluginType.SENSITIVITY, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SensitivityInterface")
        }
        activeSensitivityStore = fallbackIfNotVisible(activeSensitivityStore as PluginBase, PluginType.SENSITIVITY, jobs) as Sensitivity

        // PluginType.SMOOTHING
        pluginsInCategory = getSpecificPluginsList(PluginType.SMOOTHING)
        activeSmoothingStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SMOOTHING, jobs) as Smoothing?
        if (activeSmoothingStore == null) {
            activeSmoothingStore = getDefaultPlugin(PluginType.SMOOTHING) as Smoothing
            (activeSmoothingStore as PluginBase).setPluginEnabled(PluginType.SMOOTHING, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SmoothingInterface")
        }

        // PluginType.CALIBRATION
        pluginsInCategory = getSpecificPluginsList(PluginType.CALIBRATION)
        activeCalibrationStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.CALIBRATION, jobs) as Calibration?
        if (activeCalibrationStore == null) {
            activeCalibrationStore = getDefaultPlugin(PluginType.CALIBRATION) as Calibration
            (activeCalibrationStore as PluginBase).setPluginEnabled(PluginType.CALIBRATION, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting CalibrationInterface")
        }

        // PluginType.BGSOURCE
        pluginsInCategory = getSpecificPluginsList(PluginType.BGSOURCE)
        activeBgSourceStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.BGSOURCE, jobs) as BgSource?
        if (activeBgSourceStore == null) {
            activeBgSourceStore = getDefaultPlugin(PluginType.BGSOURCE) as BgSource
            (activeBgSourceStore as PluginBase).setPluginEnabled(PluginType.BGSOURCE, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting BgInterface")
        }

        // PluginType.PUMP
        pluginsInCategory = getSpecificPluginsList(PluginType.PUMP)
        activePumpStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP, jobs) as Pump?
        if (activePumpStore == null) {
            activePumpStore = getDefaultPlugin(PluginType.PUMP) as Pump
            (activePumpStore as PluginBase).setPluginEnabled(PluginType.PUMP, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        return jobs
    }

    /**
     * If the active plugin is no longer visible in its category (e.g., sensitivity plugin
     * incompatible with the current APS algorithm), disable it and fall back to the default.
     *
     * A plugin whose state this build ENFORCES is exempt — framework plugins use `showInList { false }`
     * to hide from the UI list but must stay functional regardless, and a forced-off plugin must not be
     * re-elected here either.
     */
    private fun fallbackIfNotVisible(active: PluginBase, type: PluginType, jobs: MutableList<Job>): PluginBase {
        if (active.enforcedState() != null) return active
        if (!active.showInList(type)) {
            active.setPluginEnabled(type, false)?.let(jobs::add)
            val default = getDefaultPlugin(type)
            default.setPluginEnabled(type, true)?.let(jobs::add)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Falling back ${type.name} from ${active.name} to ${default.name}")
            return default
        }
        return active
    }

    private fun getTheOneEnabledInArray(pluginsInCategory: ArrayList<PluginBase>, type: PluginType, jobs: MutableList<Job>): PluginBase? {
        var found: PluginBase? = null
        for (p in pluginsInCategory) {
            if (p.isEnabled(type) && found == null) {
                found = p
            } else if (p.isEnabled(type)) {
                // set others disabled
                p.setPluginEnabled(type, false)?.let(jobs::add)
            }
        }
        return found
    }

    /**
     * The first plugin enabled in the category, or null. Reads only - it changes nothing, so it is safe
     * to call from a property getter. Same answer as [getTheOneEnabledInArray], which returns the first
     * enabled one too; what this leaves out is that one's side effect of disabling the rest.
     */
    private fun firstEnabledIn(type: PluginType): PluginBase? =
        getSpecificPluginsList(type).firstOrNull { it.isEnabled(type) }

    // ***** Interface *****

    // ---------------------------------------------------------------------------------------------
    // THE THROWS BELOW ARE BY DESIGN. DO NOT SOFTEN THEM.
    //
    // "No pump selected" and its siblings are ASSERTIONS about internal state, not error handling.
    // AAPS cannot be in a state where no pump, bg source, sensitivity, smoothing or calibration is
    // selected - if one of these throws, the app is already wrong and the crash is what makes that
    // visible. The bug is always in whatever asked, and that is what gets fixed.
    //
    // So do NOT propose, and do not add:
    //   - an `activePumpOrNull` / nullable variant so a caller can skip,
    //   - a fallback to the default plugin,
    //   - a last-known-good value held over,
    //   - a try/catch at a call site.
    // Every one of those converts a loud, locatable invariant violation into silent wrong behaviour -
    // in an app that doses insulin. A pump that is quietly "not there" is worse than a crash report.
    //
    // This has been raised and rejected repeatedly. If a Crashlytics issue points here, read it as
    // "something read the active plugin at a moment when none was elected" and fix the timing at the
    // caller. A real example: `PersistentNotificationPlugin.onStart` runs again during a settings
    // import, inside the window where `loadSettings` has disabled the old pump and not yet enabled
    // the new one - the fix belongs to that window, never here.
    // ---------------------------------------------------------------------------------------------

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
            // Only during initialization: the selection is made by verifySelectionInCategories, which has
            // not run yet while ConfigBuilder is still starting the plugins. This used to call
            // getTheOneEnabledInArray, which DISABLES every later enabled pump in the category - a write,
            // and scheduled onStop jobs nobody could wait for, from inside a property read.
            ?: firstEnabledIn(PluginType.PUMP) as Pump?
            // Deliberate. See the block above the interface section - this assertion stays.
            ?: error("No pump selected")

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
