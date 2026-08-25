package app.aaps.plugins.main.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.main.general.persistentNotification.PersistentNotificationPlugin
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:main plugins into the global @AllConfigs plugin map.
 * Hilt aggregates every @InstallIn(SingletonComponent) module on the app classpath, so including
 * :plugins:main in settings.gradle is enough — no central list edit needed. See PluginsListModule
 * for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MainPluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(0)
    abstract fun bindPersistentNotificationPlugin(plugin: PersistentNotificationPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(10)
    abstract fun bindIobCobCalculatorPlugin(plugin: IobCobCalculatorPlugin): PluginBase
}
