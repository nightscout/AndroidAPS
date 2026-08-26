package app.aaps.di

import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Qualifier

/**
 * Declares the plugin multi binding maps consumed by [AppModule.Provide.providesPlugins].
 *
 * Plugins are no longer listed here: each feature module self-registers its own plugins into these
 * maps from its `*PluginsListModule` (e.g. :plugins:source [app.aaps.plugins.source.di.SourcePluginsListModule],
 * :pump:equil EquilModule). Hilt aggregates every @InstallIn(SingletonComponent) module on the app
 * classpath, so adding or removing a plugin is just an include in settings.gradle — no edit here.
 *
 * A plugin that has moved to Metro registers itself instead, on the class: `@ContributesIntoMap(
 * AppScope::class, binding = binding<PluginBase>())` plus the same `@IntKey` and the same qualifier,
 * so the ordering below is the same either way. `@AllConfigs` has no Metro counterpart - that bucket
 * is simply the unqualified one. **The scope has to move with it:** `@SingleIn(AppScope::class)`, not
 * javax `@Singleton`, because the graph that builds contributed classes is generated in `:app` and has
 * no Dagger interop, so a javax scope there is ignored and every read builds a fresh plugin. All of
 * :plugins:sync (300–370) has moved, which is why it no longer has a module of its own.
 *
 * Global @IntKey ordering — one contiguous block per feature module, step 10 within a block:
 *   0–10      general (persistent notification, iob)      :plugins:main
 *   100–120   sensitivity                                 :plugins:sensitivity
 *   200–240   aps (loop, openAPS engines, autotune)       :plugins:aps
 *   300–370   sync (sms, nsclient, upload, wear, …)       :plugins:sync
 *   400–550   bg sources                                  :plugins:source
 *   600–630   smoothing                                   :plugins:smoothing
 *   700–710   calibration                                 :plugins:calibration
 *   800–860   constraints (safety, objectives, …)         :plugins:constraints
 *   1000      VirtualPump (@AllConfigs)                    :pump:virtual
 *   1010+     real pump drivers (@PumpDriver, step 10)     :pump:* modules
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginsListModule {

    @Multibinds
    @AllConfigs
    abstract fun allConfigsPlugins(): Map<Int, PluginBase>

    @Multibinds
    @PumpDriver
    abstract fun pumpDrivers(): Map<Int, PluginBase>

    @Multibinds
    @NotNSClient
    abstract fun notNSClientPlugins(): Map<Int, PluginBase>

    @Multibinds
    @APS
    abstract fun apsPlugins(): Map<Int, PluginBase>

    @Qualifier
    annotation class Unfinished
}
