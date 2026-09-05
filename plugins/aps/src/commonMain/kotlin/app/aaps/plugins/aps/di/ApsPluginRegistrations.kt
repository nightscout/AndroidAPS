package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSAutoISF.OpenAPSAutoISFPlugin
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

/**
 * Registers the three openAPS plugins.
 *
 * The plugins are built from their own `@Inject` constructors in `commonMain`, so nothing about them
 * lives in `:app` any more. Only the registration stays behind in `androidMain`, because a plugin map
 * key is an Android concern and the qualifiers involved are JVM-only anyway.
 *
 * ## Unqualified, and that is deliberate
 *
 * The `@Binds` block this replaces said `@AllConfigs`, not `@APS` - despite living in a file called
 * `ApsPluginsModule`. `:app` merges the unqualified Metro bucket unconditionally, which is precisely
 * what `@AllConfigs` meant, so this keeps the existing behaviour.
 *
 * Reaching for `@APS` here because the module name says APS would have compiled, passed every test, and
 * quietly dropped these plugins from every follower build. The mirror of that mistake is the virtual
 * pump's, where carrying `@AllConfigs` over would have dropped the plugin from the list entirely, since
 * nothing reads a Metro map under that qualifier. Neither is visible from the annotation: the only way
 * to know is to read which bucket `MetroGraphs.allPlugins` merges, and under what condition.
 *
 * Keys 210-230, unchanged. Loop keeps 200 and Autotune 240; both also bind an interface much of the app
 * depends on, so they move separately.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApsPluginRegistrations {

    @Provides
    @IntoMap
    @IntKey(210)
    fun openApsAmaEntry(plugin: OpenAPSAMAPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @IntKey(220)
    fun openApsSmbEntry(plugin: OpenAPSSMBPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @IntKey(230)
    fun openApsAutoIsfEntry(plugin: OpenAPSAutoISFPlugin): PluginBase = plugin
}
