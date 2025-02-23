package app.aaps.core.interfaces.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.Sync

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

    /**
     * Called before importing preferences.
     * On Preferences import current content of shared preferences is cleared and then
     * new content is imported.
     *
     * This function is called BEFORE clearing and importing preferences to preserve some current values.
     * Needed content should be stored into plugin variables and then processed and restored
     * in [app.aaps.core.interfaces.plugin.PluginBaseWithPreferences.afterImport]
     *
     * App is restarted after import.
     *
     * See: [app.aaps.core.interfaces.maintenance.ImportExportPrefs.doImportSharedPreferences]
     */
    fun beforeImport()

    /**
     * Called after importing preferences.
     * On Preferences import current content of shared preferences is cleared and then
     * new content is imported.
     *
     * This function is called AFTER clearing and importing preferences to process and restore
     * saved state from [app.aaps.core.interfaces.plugin.PluginBaseWithPreferences.beforeImport]
     *
     * App is restarted after import.
     *
     * See: [app.aaps.core.interfaces.maintenance.ImportExportPrefs.doImportSharedPreferences]
     */
    fun afterImport()
}