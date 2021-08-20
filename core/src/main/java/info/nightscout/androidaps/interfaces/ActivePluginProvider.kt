package info.nightscout.androidaps.interfaces

import java.util.*

interface ActivePluginProvider {

    val activeBgSource: BgSourceInterface // Forced to Dexcom
    val activeProfileInterface: ProfileInterface // Forced to LocalProfile if not changed
    val activeInsulin: InsulinInterface // Forced to RapidActing if not changed
    val activeAPS: APSInterface // Forced to SMB
    val activePump: PumpInterface // Use in places not reachable without active pump. Otherwise IllegalStateException is thrown
    val activeSensitivity: SensitivityInterface // Forced to oref1 if not changed
    val activeTreatments: TreatmentsInterface // Forced to treatments
    val activeOverview: OverviewInterface // Forced to overview

    fun getPluginsList(): ArrayList<PluginBase>

    fun getSpecificPluginsVisibleInListByInterface(interfaceClass: Class<*>, type: PluginType): ArrayList<PluginBase>
    fun getSpecificPluginsVisibleInList(type: PluginType): ArrayList<PluginBase>
    fun getSpecificPluginsListByInterface(interfaceClass: Class<*>): ArrayList<PluginBase>
    fun verifySelectionInCategories()
}