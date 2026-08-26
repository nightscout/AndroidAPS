package app.aaps.plugins.sync.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.wear.WearPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * What is left of the :plugins:sync self-registration (@IntKey block 300-370, step 10).
 *
 * Tidepool 320, Xdrip 330, Tizen 360 and Garmin 370 now register themselves with Metro, on the class.
 * OpenHumans 340 did so earlier - see [OpenHumansMetroGraph]. See PluginsListModule for the ordering.
 *
 * ## Why these three stayed
 *
 * **A plugin can only carry Metro's `@IntKey` if nothing Dagger-injects its concrete type.** Dagger's
 * KSP `InjectProcessingStep` runs over every class it has to construct, reads the annotations on it,
 * and cannot resolve `dev.zacsweers.metro.IntKey` - it fails the build with `annotation: @IntKey =>
 * type (ERROR annotation type): error.NonExistentClass`. The other four have no such consumer, which
 * is exactly why they converted cleanly.
 *
 * These three do:
 *  - [SmsCommunicatorPlugin] - `AuthRequest`
 *  - [NSClientV3Plugin] - `NSClientV3Service` and the nine `@HiltWorker` loaders under `nsclientV3`
 *  - [WearPlugin] - `WearViewModel` and `DataLayerListenerServiceMobile`
 *
 * So they move only once those consumers stop being Dagger-built, or once the plugin is registered
 * from a `@BindingContainer` in `:app` the way `MainPluginsBindings` does for `IobCobCalculatorPlugin`
 * - which keeps the Metro annotations off the class and so keeps Dagger's processor happy.
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
    @AllConfigs
    @IntoMap
    @IntKey(350)
    abstract fun bindWearPlugin(plugin: WearPlugin): PluginBase
}
