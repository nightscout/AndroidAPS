package app.aaps.plugins.source.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.source.AidexPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.GlunovoPlugin
import app.aaps.plugins.source.IntelligoPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.NotificationReaderPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.RandomBgPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.instara.InstaraPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:source BG-source plugins into the global @AllConfigs plugin map
 * (@IntKey block 400–550, step 10). Including :plugins:source in settings.gradle is enough — no central
 * list edit needed. See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SourcePluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(400)
    abstract fun bindXdripSourcePlugin(plugin: XdripSourcePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(410)
    abstract fun bindNSClientSourcePlugin(plugin: NSClientSourcePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(420)
    abstract fun bindMM640gPlugin(plugin: MM640gPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(430)
    abstract fun bindGlimpPlugin(plugin: GlimpPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(440)
    abstract fun bindDexcomPlugin(plugin: DexcomPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(450)
    abstract fun bindAidexPlugin(plugin: AidexPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(460)
    abstract fun bindPoctechPlugin(plugin: PoctechPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(470)
    abstract fun bindTomatoPlugin(plugin: TomatoPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(480)
    abstract fun bindGlunovoPlugin(plugin: GlunovoPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(490)
    abstract fun bindIntelligoPlugin(plugin: IntelligoPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(500)
    abstract fun bindSyaiPlugin(plugin: SyaiPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(510)
    abstract fun bindPatchedSiAppPlugin(plugin: PatchedSiAppPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(520)
    abstract fun bindPatchedSinoAppPlugin(plugin: PatchedSinoAppPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(530)
    abstract fun bindNotificationReaderPlugin(plugin: NotificationReaderPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(540)
    abstract fun bindInstaraPlugin(plugin: InstaraPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(550)
    abstract fun bindRandomBgPlugin(plugin: RandomBgPlugin): PluginBase
}
