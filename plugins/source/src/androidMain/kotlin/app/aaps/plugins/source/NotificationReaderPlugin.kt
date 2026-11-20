package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcPluginByoda
import app.aaps.plugins.source.cgmapps.CgmApp
import app.aaps.plugins.source.cgmapps.CgmAppCatalog
import app.aaps.plugins.source.cgmapps.CgmApps
import app.aaps.plugins.source.cgmapps.calibrationMode
import app.aaps.plugins.source.compose.BgSourceComposeContent
import app.aaps.plugins.source.notificationreader.GlucoseDeduplicator
import app.aaps.plugins.source.notificationreader.PackageConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(530)
@SingleIn(AppScope::class)
@Inject
class NotificationReaderPlugin(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    private val cgmAppCatalog: CgmAppCatalog,
    notificationManager: NotificationManager,
) : AbstractBgSourcePlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { _ ->
            BgSourceComposeContent(
                title = rh.gs(R.string.notification_reader)
            )
        }
        .icon(IcPluginByoda)
        .pluginName(TextRef.AndroidRes(R.string.notification_reader))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_source_notification_reader)),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config, notificationManager
), BgSource {

    @Volatile private var cachedConfig: Pair<CgmApps, PackageConfig>? = null

    /** The apps of the CGM app catalog that post readings in notifications. Built again when the catalog changes. */
    val packageConfig: PackageConfig
        get() {
            val apps = cgmAppCatalog.apps
            cachedConfig?.takeIf { it.first === apps }?.let { return it.second }
            return PackageConfig.fromCatalog(apps).also { cachedConfig = apps to it }
        }

    /** Drops re-posted notifications. Kept here, not in the service, so the plugin knows which app sent the last reading. */
    val deduplicator: GlucoseDeduplicator by lazy {
        GlucoseDeduplicator(
            packageConfig = packageConfig,
            store = object : GlucoseDeduplicator.StateStore {
                override fun load(): String? = preferences.get(StringNonKey.NotificationReaderDedupState).takeIf { it.isNotBlank() }
                override fun save(json: String) = preferences.put(StringNonKey.NotificationReaderDedupState, json)
            }
        )
    }

    /** The catalog app of the newest reading, or null before the first one. Several apps can post; the newest one counts. */
    private val lastApp: CgmApp?
        get() = deduplicator.lastPackage()?.let { cgmAppCatalog.apps.byPackage(it) }

    /** The app the readings come from: the CGM button opens it, and the calibrate button when it calibrates. */
    override val appPackages: List<String>
        get() = lastApp?.packages.orEmpty()

    /** Whether that app calibrates its sensor, from the CGM app catalog. */
    override val calibrationMode: CalibrationMode
        get() = lastApp.calibrationMode()

    override suspend fun onStart() {
        super.onStart()
        Thread {
            cgmAppCatalog.updateFromRemote()
        }.start()
    }

    override fun requiredPermissions(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(PERMISSION_NOTIFICATION_LISTENER),
            rationaleTitle = TextRef.AndroidRes(R.string.permission_notification_listener_title),
            rationaleDescription = TextRef.AndroidRes(R.string.permission_notification_listener_description),
            special = true,
        )
    )

    companion object {

        const val PERMISSION_NOTIFICATION_LISTENER = "app.aaps.permission.NOTIFICATION_LISTENER"
    }
}
