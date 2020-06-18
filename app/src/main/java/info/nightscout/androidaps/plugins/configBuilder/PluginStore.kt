package info.nightscout.androidaps.plugins.configBuilder

import info.nightscout.androidaps.Config
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val config: Config
) : ActivePluginProvider {

    lateinit var plugins: List<@JvmSuppressWildcards PluginBase>

    private var activeBgSource: BgSourceInterface? = null
    private var activePump: PumpInterface? = null
    private var activeProfile: ProfileInterface? = null
    private var activeAPS: APSInterface? = null
    private var activeInsulin: InsulinInterface? = null
    private var activeSensitivity: SensitivityInterface? = null
    private var activeTreatments: TreatmentsInterface? = null

    fun loadDefaults() {
        verifySelectionInCategories()
    }

    fun getDefaultPlugin(type: PluginType): PluginBase {
        for (p in plugins)
            if (p.getType() == type && p.isDefault()) return p
        throw IllegalStateException("Default plugin not found")
    }

    fun getSpecificPluginsList(type: PluginType): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.getType() == type) newList.add(p)
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

    override fun getSpecificPluginsListByInterface(interfaceClass: Class<*>): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.javaClass != ConfigBuilderPlugin::class.java && interfaceClass.isAssignableFrom(p.javaClass)) newList.add(p)
        }
        return newList
    }

    override fun getSpecificPluginsVisibleInListByInterface(interfaceClass: Class<*>, type: PluginType): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.javaClass != ConfigBuilderPlugin::class.java && interfaceClass.isAssignableFrom(p.javaClass)) if (p.showInList(type)) newList.add(p)
        }
        return newList
    }

    override fun verifySelectionInCategories() {
        var pluginsInCategory: ArrayList<PluginBase>?

        // PluginType.APS
        if (!config.NSCLIENT && !config.PUMPCONTROL) {
            pluginsInCategory = getSpecificPluginsList(PluginType.APS)
            activeAPS = getTheOneEnabledInArray(pluginsInCategory, PluginType.APS) as APSInterface?
            if (activeAPS == null) {
                activeAPS = getDefaultPlugin(PluginType.APS) as APSInterface
                (activeAPS as PluginBase).setPluginEnabled(PluginType.APS, true)
                aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting APSInterface")
            }
            setFragmentVisiblities((activeAPS as PluginBase).name, pluginsInCategory, PluginType.APS)
        }

        // PluginType.INSULIN
        pluginsInCategory = getSpecificPluginsList(PluginType.INSULIN)
        activeInsulin = getTheOneEnabledInArray(pluginsInCategory, PluginType.INSULIN) as InsulinInterface?
        if (activeInsulin == null) {
            activeInsulin = getDefaultPlugin(PluginType.INSULIN) as InsulinInterface
            (activeInsulin as PluginBase).setPluginEnabled(PluginType.INSULIN, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting InsulinInterface")
        }
        setFragmentVisiblities((activeInsulin as PluginBase).name, pluginsInCategory, PluginType.INSULIN)

        // PluginType.SENSITIVITY
        pluginsInCategory = getSpecificPluginsList(PluginType.SENSITIVITY)
        activeSensitivity = getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY) as SensitivityInterface?
        if (activeSensitivity == null) {
            activeSensitivity = getDefaultPlugin(PluginType.SENSITIVITY) as SensitivityInterface
            (activeSensitivity as PluginBase).setPluginEnabled(PluginType.SENSITIVITY, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SensitivityInterface")
        }
        setFragmentVisiblities((activeSensitivity as PluginBase).name, pluginsInCategory, PluginType.SENSITIVITY)

        // PluginType.PROFILE
        pluginsInCategory = getSpecificPluginsList(PluginType.PROFILE)
        activeProfile = getTheOneEnabledInArray(pluginsInCategory, PluginType.PROFILE) as ProfileInterface?
        if (activeProfile == null) {
            activeProfile = getDefaultPlugin(PluginType.PROFILE) as ProfileInterface
            (activeProfile as PluginBase).setPluginEnabled(PluginType.PROFILE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting ProfileInterface")
        }
        setFragmentVisiblities((activeProfile as PluginBase).name, pluginsInCategory, PluginType.PROFILE)

        // PluginType.BGSOURCE
        pluginsInCategory = getSpecificPluginsList(PluginType.BGSOURCE)
        activeBgSource = getTheOneEnabledInArray(pluginsInCategory, PluginType.BGSOURCE) as BgSourceInterface?
        if (activeBgSource == null) {
            activeBgSource = getDefaultPlugin(PluginType.BGSOURCE) as BgSourceInterface
            (activeBgSource as PluginBase).setPluginEnabled(PluginType.BGSOURCE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting BgInterface")
        }
        setFragmentVisiblities((activeBgSource as PluginBase).name, pluginsInCategory, PluginType.PUMP)

        // PluginType.PUMP
        pluginsInCategory = getSpecificPluginsList(PluginType.PUMP)
        activePump = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP) as PumpInterface?
        if (activePump == null) {
            activePump = getDefaultPlugin(PluginType.PUMP) as PumpInterface
            (activePump as PluginBase).setPluginEnabled(PluginType.PUMP, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        setFragmentVisiblities((activePump as PluginBase).name, pluginsInCategory, PluginType.PUMP)

        // PluginType.TREATMENT
        pluginsInCategory = getSpecificPluginsList(PluginType.TREATMENT)
        activeTreatments = getTheOneEnabledInArray(pluginsInCategory, PluginType.TREATMENT) as TreatmentsInterface?
        if (activeTreatments == null) {
            activeTreatments = getDefaultPlugin(PluginType.TREATMENT) as TreatmentsInterface
            (activeTreatments as PluginBase).setPluginEnabled(PluginType.TREATMENT, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        setFragmentVisiblities((activeTreatments as PluginBase).name, pluginsInCategory, PluginType.TREATMENT)
    }

    /**
     * disables the visibility for all fragments of Plugins with the given PluginType
     * which are not equally named to the Plugin implementing the given Plugin Interface.
     *
     * @param pluginInterface
     * @param pluginType
     * @param <T>
     * @return
    </T> */
    private fun <T> determineActivePlugin(pluginInterface: Class<T>, pluginType: PluginType): T? {
        val pluginsInCategory: ArrayList<PluginBase> = getSpecificPluginsListByInterface(pluginInterface)
        return determineActivePlugin(pluginsInCategory, pluginType)
    }

    /**
     * disables the visibility for all fragments of Plugins in the given pluginsInCategory
     * with the given PluginType which are not equally named to the Plugin implementing the
     * given Plugin Interface.
     *
     *
     * TODO we are casting an interface to PluginBase, which seems to be rather odd, since
     * TODO the interface is not implementing PluginBase (this is just avoiding errors through
     * TODO conventions.
     *
     * @param pluginsInCategory
     * @param pluginType
     * @param <T>
     * @return
    </T> */
    private fun <T> determineActivePlugin(pluginsInCategory: ArrayList<PluginBase>,
                                          pluginType: PluginType): T? {
        @Suppress("UNCHECKED_CAST")
        val activePlugin = getTheOneEnabledInArray(pluginsInCategory, pluginType) as T?
        if (activePlugin != null) {
            setFragmentVisiblities((activePlugin as PluginBase).name, pluginsInCategory, pluginType)
        }
        return activePlugin
    }

    private fun setFragmentVisiblities(activePluginName: String, pluginsInCategory: ArrayList<PluginBase>,
                                       pluginType: PluginType) {
        aapsLogger.debug(LTag.CONFIGBUILDER, "Selected interface: $activePluginName")
        for (p in pluginsInCategory)
            if (p.name != activePluginName)
                p.setFragmentVisible(pluginType, false)
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

    override fun getActiveBgSource(): BgSourceInterface {
        return activeBgSource ?: checkNotNull(activeBgSource) { "No bg source selected" }
    }

    override fun getActiveProfileInterface(): ProfileInterface =
        activeProfile ?: checkNotNull(activeProfile) { "No profile selected" }

    override fun getActiveInsulin(): InsulinInterface =
        activeInsulin ?: checkNotNull(activeInsulin) { "No insulin selected" }

    override fun getActiveAPS(): APSInterface =
        activeAPS ?: checkNotNull(activeAPS) { "No APS selected" }

    override fun getActivePump(): PumpInterface =
        activePump ?: checkNotNull(activePump) { "No pump selected" }

    override fun getActiveSensitivity(): SensitivityInterface =
        activeSensitivity ?: checkNotNull(activeSensitivity) { "No sensitivity selected" }

    override fun getActiveTreatments(): TreatmentsInterface =
        activeTreatments ?: checkNotNull(activeTreatments) { "No treatments selected" }

    override fun getPluginsList(): ArrayList<PluginBase> = ArrayList(plugins)

}