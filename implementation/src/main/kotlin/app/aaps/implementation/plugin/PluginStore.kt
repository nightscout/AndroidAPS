package app.aaps.implementation.plugin

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.R
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val pumpWithConcentration: Lazy<PumpWithConcentration>
) : ActivePlugin {

    companion object {

        /** Custom identifier for the AAPS directory selection requirement. */
        const val PERMISSION_SELECT_DIRECTORY = "app.aaps.permission.SELECT_DIRECTORY"

        /** Custom identifier for notification listener access. */
        const val PERMISSION_NOTIFICATION_LISTENER = "app.aaps.permission.NOTIFICATION_LISTENER"

        private fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                for (name in flat.split(":")) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(context.packageName, cn.packageName)) return true
                }
            }
            return false
        }
    }

    lateinit var plugins: List<@JvmSuppressWildcards PluginBase>

    private fun globalPermissions(context: Context): List<PermissionGroup> = buildList {
        add(
            PermissionGroup(
                permissions = listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
                rationaleTitle = R.string.permission_battery_title,
                rationaleDescription = R.string.permission_battery_description,
                special = true,
            )
        )
        add(
            PermissionGroup(
                permissions = listOf(PERMISSION_SELECT_DIRECTORY),
                rationaleTitle = R.string.permission_directory_title,
                rationaleDescription = R.string.permission_directory_description,
                special = true,
                alwaysShowAction = true,
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // When targetSdk < 33, the system won't show a runtime permission dialog for
            // POST_NOTIFICATIONS — must open notification settings directly (special = true).
            // When targetSdk >= 33, the standard runtime dialog works (special = false).
            val needsSettingsWorkaround = context.applicationInfo.targetSdkVersion < Build.VERSION_CODES.TIRAMISU
            add(
                PermissionGroup(
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    rationaleTitle = R.string.permission_notifications_title,
                    rationaleDescription = R.string.permission_notifications_description,
                    special = needsSettingsWorkaround,
                )
            )
        }
    }

    private var activeBgSourceStore: BgSource? = null
    private var activePumpStore: Pump? = null
    private var activeAPSStore: APS? = null
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
        get() = getSpecificPluginsListByInterface(ProfileSource::class.java).first() as ProfileSource

    // App may not be initialized yet. Wait before second return
    override val activeAPS: APS
        get() = activeAPSStore ?: checkNotNull(activeAPSStore) { "No APS selected" }

    override val activePump: PumpWithConcentration
        get() = pumpWithConcentration.get()

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

    private fun isPermissionMissing(context: Context, perm: String): Boolean =
        when (perm) {
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName).not()
            }

            PERMISSION_SELECT_DIRECTORY                              ->
                preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty()

            Manifest.permission.SCHEDULE_EXACT_ALARM                 -> {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.canScheduleExactAlarms().not()
            }

            PERMISSION_NOTIFICATION_LISTENER                         ->
                !isNotificationListenerEnabled(context)

            else                                                     ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }

    override fun collectMissingPermissions(context: Context): List<PermissionGroup> {
        // Standard (non-special) plugin permissions — checked via ContextCompat
        val pluginPerms = plugins.filter { it.isEnabled() }
            .flatMap { it.missingPermissions(context) }
            .distinctBy { it.permissions.toSet() }
        // Special plugin permissions — missingPermissions() skips these, so check separately
        val specialPluginPerms = plugins.filter { it.isEnabled() }
            .flatMap { it.requiredPermissions().filter { group -> group.special } }
            .filter { group -> group.permissions.any { perm -> isPermissionMissing(context, perm) } }
            .distinctBy { it.permissions.toSet() }
        // Global permissions (battery, directory)
        val globalMissing = globalPermissions(context).filter { group ->
            group.permissions.any { perm -> isPermissionMissing(context, perm) }
        }
        return (globalMissing + pluginPerms + specialPluginPerms).distinctBy { it.permissions.toSet() }
    }

    override fun collectAllPermissions(context: Context): List<PermissionGroup> =
        (globalPermissions(context) + plugins.filter { it.isEnabled() }.flatMap { it.requiredPermissions() })
            .distinctBy { it.permissions.toSet() }

}