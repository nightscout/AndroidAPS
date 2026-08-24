package app.aaps.plugins.constraints.di

import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

/**
 * Metro wiring for this module's seven plugins, replacing `ConstraintsPluginsListModule`.
 *
 * The three maps are the point. Each plugin belongs to exactly one build bucket, and `:app` merges
 * each bucket under its own condition: `@AllConfigs` always, `@APS` only when the build runs a loop,
 * `@NotNSClient` only when the build is not a follower. Put a plugin in the wrong map and it either
 * disappears from the app or turns up in a build that has never shown it - and nothing would fail,
 * because a plugin list is just a list.
 *
 * That is also why this module has Dagger interop switched on. Without it Metro ignores the javax
 * qualifiers on the bindings it reads and matches on type alone, and every one of these is the same
 * type. `ConstraintsBucketsTest` checks the split rather than trusting it.
 *
 * The plugins themselves are untouched: they keep their `javax.inject` constructors, which interop
 * lets Metro read. Converting a module is moving the wiring, not rewriting the classes.
 */
@DependencyGraph(AppScope::class)
internal interface ConstraintsMetroGraph {

    /** Present in every build. */
    @AllConfigs
    val allConfigsPlugins: Map<Int, PluginBase>

    /** Only in builds that run the loop. */
    @APS
    val apsPlugins: Map<Int, PluginBase>

    /** Only in builds that are not a follower. */
    @NotNSClient
    val notNsClientPlugins: Map<Int, PluginBase>

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides safetyRef: DeferredRef<SafetyPlugin>,
            @Provides versionCheckerRef: DeferredRef<VersionCheckerPlugin>,
            @Provides storageConstraintRef: DeferredRef<StorageConstraintPlugin>,
            @Provides signatureVerifierRef: DeferredRef<SignatureVerifierPlugin>,
            @Provides objectivesRef: DeferredRef<ObjectivesPlugin>,
            @Provides dstHelperRef: DeferredRef<DstHelperPlugin>,
            @Provides bgQualityCheckRef: DeferredRef<BgQualityCheckPlugin>
        ): ConstraintsMetroGraph
    }

    // The plugins arrive already built, from Dagger. They are deferred for the re-entrancy reason in
    // MetroGraphs: a plugin's own dependencies lead back to the plugin list, which asks this graph.
    @Provides fun safety(r: DeferredRef<SafetyPlugin>): SafetyPlugin = r.get()
    @Provides fun versionChecker(r: DeferredRef<VersionCheckerPlugin>): VersionCheckerPlugin = r.get()
    @Provides fun storageConstraint(r: DeferredRef<StorageConstraintPlugin>): StorageConstraintPlugin = r.get()
    @Provides fun signatureVerifier(r: DeferredRef<SignatureVerifierPlugin>): SignatureVerifierPlugin = r.get()
    @Provides fun objectives(r: DeferredRef<ObjectivesPlugin>): ObjectivesPlugin = r.get()
    @Provides fun dstHelper(r: DeferredRef<DstHelperPlugin>): DstHelperPlugin = r.get()
    @Provides fun bgQualityCheck(r: DeferredRef<BgQualityCheckPlugin>): BgQualityCheckPlugin = r.get()

    @Provides @AllConfigs @IntoMap @IntKey(800)
    fun bindSafety(plugin: SafetyPlugin): PluginBase = plugin

    @Provides @NotNSClient @IntoMap @IntKey(810)
    fun bindVersionChecker(plugin: VersionCheckerPlugin): PluginBase = plugin

    @Provides @APS @IntoMap @IntKey(820)
    fun bindStorageConstraint(plugin: StorageConstraintPlugin): PluginBase = plugin

    @Provides @APS @IntoMap @IntKey(830)
    fun bindSignatureVerifier(plugin: SignatureVerifierPlugin): PluginBase = plugin

    @Provides @APS @IntoMap @IntKey(840)
    fun bindObjectives(plugin: ObjectivesPlugin): PluginBase = plugin

    @Provides @AllConfigs @IntoMap @IntKey(850)
    fun bindDstHelper(plugin: DstHelperPlugin): PluginBase = plugin

    @Provides @AllConfigs @IntoMap @IntKey(860)
    fun bindBgQualityCheck(plugin: BgQualityCheckPlugin): PluginBase = plugin
}
