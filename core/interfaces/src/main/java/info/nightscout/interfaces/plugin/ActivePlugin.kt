package info.nightscout.interfaces.plugin

import info.nightscout.interfaces.Overview
import info.nightscout.interfaces.aps.APS
import info.nightscout.interfaces.aps.Sensitivity
import info.nightscout.interfaces.constraints.Objectives
import info.nightscout.interfaces.constraints.Safety
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.smoothing.Smoothing
import info.nightscout.interfaces.source.BgSource
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.interfaces.sync.Sync

interface ActivePlugin {

    /**
     *  Currently selected BgSource plugin
     *  Default to Dexcom
     */
    val activeBgSource: BgSource

    /**
     *  Currently selected Profile plugin
     *  Default LocalProfile
     */
    val activeProfileSource: ProfileSource

    /**
     *  Currently selected Insulin plugin
     *  Default RapidActing
     */
    val activeInsulin: Insulin

    /**
     *  Currently selected APS plugin
     *  Default SMB
     */
    val activeAPS: APS

    /**
     *  Currently selected Pump plugin
     *  Default VirtualPump
     */
    val activePump: Pump

    /**
     *  Currently selected Sensitivity plugin
     *  Default Oref1
     */
    val activeSensitivity: Sensitivity

    /**
     *  Currently selected Overview plugin
     *  Always OverviewPlugin
     */
    val activeOverview: Overview

    /**
     *  Currently selected Safety plugin
     *  Always SafetyPlugin
     */
    val activeSafety: Safety

    /**
     *  Currently selected Safety plugin
     *  Always IobCobCalculatorPlugin
     */
    val activeIobCobCalculator: IobCobCalculator

    /**
     *  Objectives plugin
     */
    val activeObjectives: Objectives?

    /**
     *  Smoothing plugin
     */
    val activeSmoothing: Smoothing

    /**
     *  Currently selected NsClient plugin
     */
    val activeNsClient: NsClient?

    /**
     *  Currently selected Sync plugin
     */
    val firstActiveSync: Sync?
    val activeSyncs: ArrayList<Sync>

    /**
     *  List of all registered plugins
     */
    fun getPluginsList(): ArrayList<PluginBase>

    /**
     *  List of all plugins of type marked as ShowInList
     *  (for ConfigBuilder UI)
     */
    fun getSpecificPluginsVisibleInList(type: PluginType): ArrayList<PluginBase>

    /**
     *  List of all plugins implementing interface
     */
    fun getSpecificPluginsListByInterface(interfaceClass: Class<*>): ArrayList<PluginBase>

    /**
     *  Pre-process all plugin types and validate active plugins (ie. only only one plugin for type is selected)
     */
    fun verifySelectionInCategories()

    /**
     *  List of all plugins of type
     */
    fun getSpecificPluginsList(type: PluginType): ArrayList<PluginBase>

    fun loadDefaults()
}