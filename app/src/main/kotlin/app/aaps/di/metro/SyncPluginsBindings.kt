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
 * ## Why not annotate the class, like the other four
 * ```
 * InjectProcessingStep was unable to process 'NSClientV3Plugin(...)'
 *   => annotation: @IntKey  => type (ERROR annotation type): error.NonExistentClass
 * ```
 * ## Who owns the instance
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
