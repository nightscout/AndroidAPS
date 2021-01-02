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

    private var activeBgSourceStore: BgSourceInterface? = null
    private var activePumpStore: PumpInterface? = null
    private var activeProfile: ProfileInterface? = null
    private var activeAPSStore: APSInterface? = null
    private var activeInsulinStore: InsulinInterface? = null
    private var activeSensitivityStore: SensitivityInterface? = null
    private var activeTreatmentsStore: TreatmentsInterface? = null

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
            activeAPSStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.APS) as APSInterface?
            if (activeAPSStore == null) {
                activeAPSStore = getDefaultPlugin(PluginType.APS) as APSInterface
                (activeAPSStore as PluginBase).setPluginEnabled(PluginType.APS, true)
                aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting APSInterface")
            }
            setFragmentVisiblities((activeAPSStore as PluginBase).name, pluginsInCategory, PluginType.APS)
        }

        // PluginType.INSULIN
        pluginsInCategory = getSpecificPluginsList(PluginType.INSULIN)
        activeInsulinStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.INSULIN) as InsulinInterface?
        if (activeInsulinStore == null) {
            activeInsulinStore = getDefaultPlugin(PluginType.INSULIN) as InsulinInterface
            (activeInsulinStore as PluginBase).setPluginEnabled(PluginType.INSULIN, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting InsulinInterface")
        }
        setFragmentVisiblities((activeInsulinStore as PluginBase).name, pluginsInCategory, PluginType.INSULIN)

        // PluginType.SENSITIVITY
        pluginsInCategory = getSpecificPluginsList(PluginType.SENSITIVITY)
        activeSensitivityStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY) as SensitivityInterface?
        if (activeSensitivityStore == null) {
            activeSensitivityStore = getDefaultPlugin(PluginType.SENSITIVITY) as SensitivityInterface
            (activeSensitivityStore as PluginBase).setPluginEnabled(PluginType.SENSITIVITY, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SensitivityInterface")
        }
        setFragmentVisiblities((activeSensitivityStore as PluginBase).name, pluginsInCategory, PluginType.SENSITIVITY)

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
        activeBgSourceStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.BGSOURCE) as BgSourceInterface?
        if (activeBgSourceStore == null) {
            activeBgSourceStore = getDefaultPlugin(PluginType.BGSOURCE) as BgSourceInterface
            (activeBgSourceStore as PluginBase).setPluginEnabled(PluginType.BGSOURCE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting BgInterface")
        }
        setFragmentVisiblities((activeBgSourceStore as PluginBase).name, pluginsInCategory, PluginType.PUMP)

        // PluginType.PUMP
        pluginsInCategory = getSpecificPluginsList(PluginType.PUMP)
        activePumpStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP) as PumpInterface?
        if (activePumpStore == null) {
            activePumpStore = getDefaultPlugin(PluginType.PUMP) as PumpInterface
            (activePumpStore as PluginBase).setPluginEnabled(PluginType.PUMP, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        setFragmentVisiblities((activePumpStore as PluginBase).name, pluginsInCategory, PluginType.PUMP)

        // PluginType.TREATMENT
        pluginsInCategory = getSpecificPluginsList(PluginType.TREATMENT)
        activeTreatmentsStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.TREATMENT) as TreatmentsInterface?
        if (activeTreatmentsStore == null) {
            activeTreatmentsStore = getDefaultPlugin(PluginType.TREATMENT) as TreatmentsInterface
            (activeTreatmentsStore as PluginBase).setPluginEnabled(PluginType.TREATMENT, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        setFragmentVisiblities((activeTreatmentsStore as PluginBase).name, pluginsInCategory, PluginType.TREATMENT)
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

    override val activeBgSource: BgSourceInterface
        get() = activeBgSourceStore ?: checkNotNull(activeBgSourceStore) { "No bg source selected" }

    override val activeProfileInterface: ProfileInterface
        get() = activeProfile ?: checkNotNull(activeProfile) { "No profile selected" }

    override val activeInsulin: InsulinInterface
        get() = activeInsulinStore ?: checkNotNull(activeInsulinStore) { "No insulin selected" }

    override val activeAPS: APSInterface
        get() = activeAPSStore ?: checkNotNull(activeAPSStore) { "No APS selected" }

    override val activePump: PumpInterface
        get() = activePumpStore ?: checkNotNull(activePumpStore) { "No pump selected" }

    override val activeSensitivity: SensitivityInterface
        get() = activeSensitivityStore
            ?: checkNotNull(activeSensitivityStore) { "No sensitivity selected" }

    override val activeTreatments: TreatmentsInterface
        get() = activeTreatmentsStore
            ?: checkNotNull(activeTreatmentsStore) { "No treatments selected" }

    override val activeOverview: OverviewInterface
        get() = getSpecificPluginsListByInterface(OverviewInterface::class.java).first() as OverviewInterface

    override fun getPluginsList(): ArrayList<PluginBase> = ArrayList(plugins)

}