package app.aaps.pump.insight.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.insight.InsightPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        InsightCommModule::class,
        InsightActivitiesModule::class,
        InsightServicesModule::class,
        InsightDatabaseModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class InsightModule {

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1050)
    abstract fun bindInsightPlugin(plugin: InsightPlugin): PluginBase
}
