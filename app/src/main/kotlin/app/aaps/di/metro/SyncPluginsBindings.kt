package app.aaps.di.metro

import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The one `:plugins:sync` plugin that cannot register itself.
 *
 * ## Why not annotate the class, like the other three
 *
 * Its map entry is qualified with [NotNSClient], and a qualifier has to travel with the entry rather
 * than sit on the class - `@NotNSClient` on `SmsCommunicatorPlugin` would qualify every binding it
 * has, including the `SmsCommunicator` interface nobody asks for that way. Metro documents putting
 * the qualifier on the bound type instead, but `binding<@NotNSClient PluginBase>()` is rejected by
 * the version pinned here:
 * ```
 * Inapplicable candidate(s): constructor(scope: KClass<*>, binding: binding<*> = ..., ...)
 * ```
 * So the entry stays stated. Worth retrying when Metro is un-pinned from the snapshot - if it is
 * accepted then, this file goes and the annotation moves onto the class.
 *
 * **The previous reason recorded here was wrong.** It quoted `InjectProcessingStep was unable to
 * process` and `error.NonExistentClass`, which are Dagger/KAPT diagnostics - Metro is a compiler
 * plugin and has no annotation processor. `NSClientV3Plugin` and `WearPlugin` now carry
 * `@ContributesIntoMap` and compile, which settles it. The real obstacle for those two was only that
 * `dev.zacsweers.metro.IntKey` clashes with `app.aaps.core.keys.IntKey`; they import it as
 * `MetroIntKey`, the same way `LoopPlugin` and `AutotunePlugin` always have.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SyncPluginsBindings {

    @Provides
    @IntoMap
    @NotNSClient
    @IntKey(300)
    fun smsCommunicatorPlugin(plugin: SmsCommunicatorPlugin): PluginBase = plugin
}
