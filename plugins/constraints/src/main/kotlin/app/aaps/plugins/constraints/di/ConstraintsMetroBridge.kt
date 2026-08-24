package app.aaps.plugins.constraints.di

import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerPlugin
import dev.zacsweers.metro.createGraphFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hands this module's plugins from Dagger to [ConstraintsMetroGraph] and passes the three buckets back.
 *
 * The module owns its bridge because it is a plain Android library. A multiplatform module cannot do
 * this - Dagger has no Java step there - so those keep their bridge in `:app`. See
 * `_docs/METRO_MIGRATION_NEXT_STEPS.md`.
 */
@Singleton
class ConstraintsMetroBridge @Inject constructor(
    private val safety: Provider<SafetyPlugin>,
    private val versionChecker: Provider<VersionCheckerPlugin>,
    private val storageConstraint: Provider<StorageConstraintPlugin>,
    private val signatureVerifier: Provider<SignatureVerifierPlugin>,
    private val objectives: Provider<ObjectivesPlugin>,
    private val dstHelper: Provider<DstHelperPlugin>,
    private val bgQualityCheck: Provider<BgQualityCheckPlugin>
) {

    private val graph: ConstraintsMetroGraph by lazy {
        createGraphFactory<ConstraintsMetroGraph.Factory>().create(
            DeferredRef { safety.get() },
            DeferredRef { versionChecker.get() },
            DeferredRef { storageConstraint.get() },
            DeferredRef { signatureVerifier.get() },
            DeferredRef { objectives.get() },
            DeferredRef { dstHelper.get() },
            DeferredRef { bgQualityCheck.get() }
        )
    }

    /** Merge each of these under the same condition the matching Dagger qualifier had. */
    val allConfigsPlugins: Map<Int, PluginBase> get() = graph.allConfigsPlugins
    val apsPlugins: Map<Int, PluginBase> get() = graph.apsPlugins
    val notNsClientPlugins: Map<Int, PluginBase> get() = graph.notNsClientPlugins
}
