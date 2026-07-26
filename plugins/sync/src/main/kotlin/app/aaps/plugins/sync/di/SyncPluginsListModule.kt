package app.aaps.plugins.sync.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.sync.garmin.GarminPlugin
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.tidepool.TidepoolPlugin
import app.aaps.plugins.sync.tizen.TizenPlugin
import app.aaps.plugins.sync.wear.WearPlugin
import app.aaps.plugins.sync.xdrip.XdripPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:sync plugins into the plugin maps (@IntKey block 300–370, step 10).
 * SmsCommunicator/Tidepool/OpenHumans are @NotNSClient; NSClientV3/Xdrip/Wear/Tizen/Garmin are @AllConfigs.
 * Including :plugins:sync in settings.gradle is enough — no central list edit needed.
 * See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SyncPluginsListModule {

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(300)
    abstract fun bindSmsCommunicatorPlugin(plugin: SmsCommunicatorPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(310)
    abstract fun bindNSClientV3Plugin(plugin: NSClientV3Plugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(320)
    abstract fun bindTidepoolPlugin(plugin: TidepoolPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(330)
    abstract fun bindXdripPlugin(plugin: XdripPlugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(340)
    abstract fun bindsOpenHumansPlugin(plugin: OpenHumansUploaderPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(350)
    abstract fun bindWearPlugin(plugin: WearPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(360)
    abstract fun bindDataBroadcastPlugin(plugin: TizenPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(370)
    abstract fun bindGarminPlugin(plugin: GarminPlugin): PluginBase
}
