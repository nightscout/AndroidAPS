package app.aaps.plugins.constraints.di

import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:constraints plugins into the plugin maps (@IntKey block 800–860, step 10).
 * Safety/DstHelper/BgQualityCheck are @AllConfigs; VersionChecker is @NotNSClient; StorageConstraint/
 * SignatureVerifier/Objectives are @APS. Including :plugins:constraints in settings.gradle is enough —
 * no central list edit needed. See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ConstraintsPluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(800)
    abstract fun bindSafetyPlugin(plugin: SafetyPlugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(810)
    abstract fun bindVersionCheckerPlugin(plugin: VersionCheckerPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(820)
    abstract fun bindStorageConstraintPlugin(plugin: StorageConstraintPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(830)
    abstract fun bindSignatureVerifierPlugin(plugin: SignatureVerifierPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(840)
    abstract fun bindObjectivesPlugin(plugin: ObjectivesPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(850)
    abstract fun bindDstHelperPlugin(plugin: DstHelperPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(860)
    abstract fun bindBgQualityCheckPlugin(plugin: BgQualityCheckPlugin): PluginBase
}
