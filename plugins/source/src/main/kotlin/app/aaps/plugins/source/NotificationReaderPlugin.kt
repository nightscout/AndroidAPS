package app.aaps.plugins.source

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginByoda
import app.aaps.plugins.source.compose.BgSourceComposeContent
import app.aaps.plugins.source.notificationreader.PackageConfig
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationReaderPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    private val context: Context,
) : AbstractBgSourcePlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { _ ->
            BgSourceComposeContent(
                title = rh.gs(R.string.notification_reader)
            )
        }
        .icon(IcPluginByoda)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.notification_reader)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_notification_reader),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource {

    @Volatile var packageConfig: PackageConfig = loadPackageConfig()
        private set

    override fun onStart() {
        super.onStart()
        Thread {
            updateDefinitionsFromRemote()
        }.start()
    }

    override fun requiredPermissions(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(PERMISSION_NOTIFICATION_LISTENER),
            rationaleTitle = R.string.permission_notification_listener_title,
            rationaleDescription = R.string.permission_notification_listener_description,
            special = true,
        )
    )

    private fun loadPackageConfig(): PackageConfig {
        return try {
            val stored = preferences.get(StringNonKey.NotificationReaderPackages)
            val json = if (stored.isNotEmpty()) {
                stored
            } else {
                val asset = context.assets.open(PACKAGE_CONFIG_ASSET).bufferedReader().use { it.readText() }
                preferences.put(StringNonKey.NotificationReaderPackages, asset)
                asset
            }
            PackageConfig.fromJson(json)
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Failed to load package config", e)
            PackageConfig(0, emptySet(), emptyMap())
        }
    }

    private fun updateDefinitionsFromRemote() {
        try {
            val remoteJson = URL(REMOTE_DEFINITIONS_URL).readText()
            val remoteConfig = PackageConfig.fromJson(remoteJson)
            if (remoteConfig.version > packageConfig.version) {
                aapsLogger.info(LTag.BGSOURCE, "Updating notification reader definitions: v${packageConfig.version} → v${remoteConfig.version}")
                preferences.put(StringNonKey.NotificationReaderPackages, remoteJson)
                packageConfig = remoteConfig
            } else {
                aapsLogger.debug(LTag.BGSOURCE, "Notification reader definitions up to date (v${packageConfig.version})")
            }
        } catch (e: Exception) {
            aapsLogger.debug(LTag.BGSOURCE, "Failed to fetch remote definitions: ${e.message}")
        }
    }

    companion object {

        const val PERMISSION_NOTIFICATION_LISTENER = "app.aaps.permission.NOTIFICATION_LISTENER"
        private const val PACKAGE_CONFIG_ASSET = "notification_reader_packages.json"
        private const val REMOTE_DEFINITIONS_URL = "https://raw.githubusercontent.com/nightscout/AndroidAPS/refs/heads/versions/notification_reader_packages.json"
    }
}
