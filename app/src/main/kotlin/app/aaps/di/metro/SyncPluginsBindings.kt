package app.aaps.di.metro

import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.wear.WearPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The last three `:plugins:sync` plugins, registered from here rather than from the class.
 *
 * ## Why not annotate the class, like the other four
 *
 * Dagger's KSP `InjectProcessingStep` runs over every class it has to construct, reads the annotations
 * on it, and cannot resolve `dev.zacsweers.metro.IntKey`:
 *
 * ```
 * InjectProcessingStep was unable to process 'NSClientV3Plugin(...)'
 *   => annotation: @IntKey  => type (ERROR annotation type): error.NonExistentClass
 * ```
 *
 * Tidepool, Xdrip, Tizen and Garmin have no Dagger-built consumer, so Dagger never processes them and
 * they carry their own `@ContributesIntoMap`. These three do: `AuthRequest` injects
 * [SmsCommunicatorPlugin], the nine `@HiltWorker` loaders inject [NSClientV3Plugin], and the wear data
 * layer injects [WearPlugin]. Registering the entry here keeps the Metro annotations off the class.
 *
 * ## Who owns the instance
 *
 * **Dagger**, for the same reason it owns the pump drivers: the classes above are Dagger-built, so
 * Dagger's copy is the live one. These take the plugin from `AapsLeaves`, which hands Dagger's singleton
 * over, so the object in the plugin map is the object those consumers use. Giving Metro its own would be
 * the split-brain that `PumpLeaves` documents - a plugin in the list that nothing ever writes to.
 *
 * `@SingleIn` here does not compete with that: it makes Metro hold **one reference to Dagger's object**
 * rather than calling the leaf on every read. Without it `ContributedPluginsTest` fails with "plugin 350
 * is rebuilt on every read", because a plugin has to be one object for its enabled state to mean
 * anything.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SyncPluginsBindings {

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @NotNSClient
    @IntKey(300)
    fun smsCommunicatorPlugin(plugin: SmsCommunicatorPlugin): PluginBase = plugin

    /**
     * The same plugin under its interface, mirroring the `@Binds` in `SyncModule` that Dagger consumers
     * still use. Both resolve through the `smsCommunicatorPlugin` leaf, so the two frameworks hand out
     * the one object Dagger built - there is no second SmsCommunicator.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun smsCommunicator(plugin: SmsCommunicatorPlugin): SmsCommunicator = plugin

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @IntKey(310)
    fun nsClientV3Plugin(plugin: NSClientV3Plugin): PluginBase = plugin

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @IntKey(350)
    fun wearPlugin(plugin: WearPlugin): PluginBase = plugin
}
