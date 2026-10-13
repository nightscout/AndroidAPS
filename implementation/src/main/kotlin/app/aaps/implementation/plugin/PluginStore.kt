package app.aaps.implementation.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.Sync
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginStore @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ActivePlugin {

    lateinit var plugins: List<@JvmSuppressWildcards PluginBase>

    private var activeBgSourceStore: BgSource? = null
    private var activePumpStore: Pump? = null
    private var activeProfile: ProfileSource? = null
    private var activeAPSStore: APS? = null
    private var activeInsulinStore: Insulin? = null
    private var activeSensitivityStore: Sensitivity? = null
    private var activeSmoothingStore: Smoothing? = null

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

    override fun getSpecificPluginsListByInterface(interfaceClass: Class<*>): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (!interfaceClass.isAssignableFrom(ConfigBuilder::class.java) && interfaceClass.isAssignableFrom(p.javaClass)) newList.add(p)
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
        setFragmentVisibilities((activeAPSStore as PluginBase).name, pluginsInCategory, PluginType.APS)

        // PluginType.INSULIN
        pluginsInCategory = getSpecificPluginsList(PluginType.INSULIN)
        activeInsulinStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.INSULIN) as Insulin?
        if (activeInsulinStore == null) {
            activeInsulinStore = getDefaultPlugin(PluginType.INSULIN) as Insulin
            (activeInsulinStore as PluginBase).setPluginEnabled(PluginType.INSULIN, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting InsulinInterface")
        }
        setFragmentVisibilities((activeInsulinStore as PluginBase).name, pluginsInCategory, PluginType.INSULIN)

        // PluginType.SENSITIVITY
        pluginsInCategory = getSpecificPluginsList(PluginType.SENSITIVITY)
        activeSensitivityStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY) as Sensitivity?
        if (activeSensitivityStore == null) {
            activeSensitivityStore = getDefaultPlugin(PluginType.SENSITIVITY) as Sensitivity
            (activeSensitivityStore as PluginBase).setPluginEnabled(PluginType.SENSITIVITY, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SensitivityInterface")
        }
        setFragmentVisibilities((activeSensitivityStore as PluginBase).name, pluginsInCategory, PluginType.SENSITIVITY)

        // PluginType.SMOOTHING
        pluginsInCategory = getSpecificPluginsList(PluginType.SMOOTHING)
        activeSmoothingStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SMOOTHING) as Smoothing?
        if (activeSmoothingStore == null) {
            activeSmoothingStore = getDefaultPlugin(PluginType.SMOOTHING) as Smoothing
            (activeSmoothingStore as PluginBase).setPluginEnabled(PluginType.SMOOTHING, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SmoothingInterface")
        }
        setFragmentVisibilities((activeSmoothingStore as PluginBase).name, pluginsInCategory, PluginType.SMOOTHING)

        // PluginType.PROFILE
        pluginsInCategory = getSpecificPluginsList(PluginType.PROFILE)
        activeProfile = getTheOneEnabledInArray(pluginsInCategory, PluginType.PROFILE) as ProfileSource?
        if (activeProfile == null) {
            activeProfile = getDefaultPlugin(PluginType.PROFILE) as ProfileSource
            (activeProfile as PluginBase).setPluginEnabled(PluginType.PROFILE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting ProfileInterface")
        }
        setFragmentVisibilities((activeProfile as PluginBase).name, pluginsInCategory, PluginType.PROFILE)

        // PluginType.BGSOURCE
        pluginsInCategory = getSpecificPluginsList(PluginType.BGSOURCE)
        activeBgSourceStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.BGSOURCE) as BgSource?
        if (activeBgSourceStore == null) {
            activeBgSourceStore = getDefaultPlugin(PluginType.BGSOURCE) as BgSource
            (activeBgSourceStore as PluginBase).setPluginEnabled(PluginType.BGSOURCE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting BgInterface")
        }
        setFragmentVisibilities((activeBgSourceStore as PluginBase).name, pluginsInCategory, PluginType.BGSOURCE)

        // PluginType.PUMP
        pluginsInCategory = getSpecificPluginsList(PluginType.PUMP)
        activePumpStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP) as Pump?
        if (activePumpStore == null) {
            activePumpStore = getDefaultPlugin(PluginType.PUMP) as Pump
            (activePumpStore as PluginBase).setPluginEnabled(PluginType.PUMP, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        setFragmentVisibilities((activePumpStore as PluginBase).name, pluginsInCategory, PluginType.PUMP)
    }

    private fun setFragmentVisibilities(
        activePluginName: String, pluginsInCategory: ArrayList<PluginBase>,
        pluginType: PluginType
    ) {
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

    override val activeBgSource: BgSource
        get() = activeBgSourceStore ?: checkNotNull(activeBgSourceStore) { "No bg source selected" }

    override val activeProfileSource: ProfileSource
        get() = activeProfile ?: checkNotNull(activeProfile) { "No profile selected" }

    override val activeInsulin: Insulin
        get() = activeInsulinStore ?: getDefaultPlugin(PluginType.INSULIN) as Insulin

    // App may not be initialized yet. Wait before second return
    override val activeAPS: APS
        get() = activeAPSStore ?: checkNotNull(activeAPSStore) { "No APS selected" }

    override val activePump: Pump
        get() = activePumpStore
        // Following line can be used only during initialization
            ?: getTheOneEnabledInArray(getSpecificPluginsList(PluginType.PUMP), PluginType.PUMP) as Pump?
            ?: checkNotNull(activePumpStore) { "No pump selected" }

    override val activeSensitivity: Sensitivity
        get() = activeSensitivityStore ?: checkNotNull(activeSensitivityStore) { "No sensitivity selected" }

    override val activeSmoothing: Smoothing
        get() = activeSmoothingStore ?: checkNotNull(activeSmoothingStore) { "No smoothing selected" }

    override val activeOverview: Overview
        get() = getSpecificPluginsListByInterface(Overview::class.java).first() as Overview

    override val activeSafety: Safety
        get() = getSpecificPluginsListByInterface(Safety::class.java).first() as Safety

    override val activeIobCobCalculator: IobCobCalculator
        get() = getSpecificPluginsListByInterface(IobCobCalculator::class.java).first() as IobCobCalculator
    override val activeObjectives: Objectives?
        get() = getSpecificPluginsListByInterface(Objectives::class.java).firstOrNull() as Objectives?
    override val activeNsClient: NsClient?
        get() = getTheOneEnabledInArray(getSpecificPluginsListByInterface(NsClient::class.java), PluginType.SYNC) as NsClient?

    @Suppress("UNCHECKED_CAST")
    override val firstActiveSync: Sync?
        get() = (getSpecificPluginsList(PluginType.SYNC) as ArrayList<Sync>).firstOrNull { it.connected }

    @Suppress("UNCHECKED_CAST")
    override val activeSyncs: ArrayList<Sync>
        get() = getSpecificPluginsList(PluginType.SYNC) as ArrayList<Sync>

    override fun getPluginsList(): ArrayList<PluginBase> = ArrayList(plugins)

}